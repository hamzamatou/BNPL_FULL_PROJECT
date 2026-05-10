import re
from typing import Any, Dict, List, Tuple
from concurrent.futures import ThreadPoolExecutor

from rapidfuzz import fuzz

from app.config import Settings
from app.services.ocr_service import extract_text_from_document
from app.services.ollama_service import (
    extract_structured_fields_for_dossier,
    get_target_field,
)
from app.services.cin_label_scan import (
    extract_cin_labeled_per_document,
    has_cin_label,
)

# =========================
# OUTILS
# =========================

DIGITS_ARABES = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")


def normaliser_texte(v: Any) -> str:
    return str(v or "").strip().lower()


def normaliser_cin(v: Any) -> str:
    return "".join(
        c for c in str(v or "").translate(DIGITS_ARABES)
        if c.isdigit()
    )


def convertir_float(v: Any) -> float | None:
    if v in (None, ""):
        return None
    try:
        return float(str(v).replace(",", ".").replace(" ", ""))
    except ValueError:
        return None


def convertir_int(v: Any) -> int | None:
    if v in (None, ""):
        return None
    try:
        return int(float(str(v).replace(",", ".").strip()))
    except Exception:
        m = re.search(r"-?\d+", str(v))
        return int(m.group(0)) if m else None


# =========================
# ERREUR BLOQUANTE
# =========================

def erreur_bloquante(code: str, message: str, details: Dict[str, Any] | None = None):
    return {
        "score_coherence": 0,
        "anomalies": [{
            "code": code,
            "niveau": "BLOQUANT",
            "message": message,
            "details": details or {}
        }],
        "corrections": {}
    }


# =========================
# PIPELINE PRINCIPAL
# =========================

def verifier_coherence_dossier(donnees_declarees: Dict[str, Any], fichiers) -> Dict[str, Any]:

    # 1. documents requis
    docs_requis = calculer_documents_requis(donnees_declarees)

    manquants = [d for d in docs_requis if fichiers.get(d) is None]
    if manquants:
        return {
            "message": "Documents manquants",
            "documents_manquants": sorted(manquants),
        }

    # 2. lecture fichiers
    fichiers_bytes: List[Tuple[str, bytes]] = [
        (d, fichiers[d].read()) for d in docs_requis if fichiers.get(d)
    ]

    # 3. OCR
    textes_ocr = executer_ocr_parallel(fichiers_bytes)

    # =========================
    # 4. PRECHECK CIN
    # =========================

    cins_par_doc = extract_cin_labeled_per_document(textes_ocr)

    cins_valides = {
        doc: normaliser_cin(v)
        for doc, v in cins_par_doc.items()
        if v
    }

    cins_uniques = {
        c for c in cins_valides.values()
        if len(c) == 8
    }

    cin_extrait = next(iter(cins_uniques), None)
    cin_decl = normaliser_cin(donnees_declarees.get("cin"))

    # CIN déclaré ≠ extrait
    if cin_decl and cin_extrait and cin_decl != cin_extrait:
        return erreur_bloquante(
            "COH_CIN_MISMATCH",
            "CIN déclaré différent du document",
            {
                "cin_declare": cin_decl,
                "cin_extrait": cin_extrait,
                "cin_par_document": cins_valides
            }
        )

    # CIN différents entre documents
    if len(cins_uniques) > 1:
        return erreur_bloquante(
            "COH_CIN_INTER_DOCUMENTS",
            "CIN différents entre documents",
            {
                "cin_par_document": cins_valides
            }
        )

    cin_reference = cin_extrait or cin_decl

    # =========================
    # 5. EXTRACTION IA
    # =========================

    extractions = extract_structured_fields_for_dossier(
        textes_ocr,
        docs_requis
    )

    # =========================
    # 6. FUSION
    # =========================

    fusion = fusionner_extractions(extractions)

    # =========================
    # 7. COHERENCE METIER
    # =========================

    return verifier_coherence_metier(
        donnees_declarees,
        fusion,
        cin_reference
    )


# =========================
# OCR PARALLELE
# =========================

def executer_ocr_parallel(fichiers_bytes: List[Tuple[str, bytes]]) -> Dict[str, str]:
    if not fichiers_bytes:
        return {}

    workers = min(max(1, Settings.COHERENCE_OCR_WORKERS), len(fichiers_bytes))

    def traiter(p):
        doc, raw = p
        return doc, extract_text_from_document(raw)

    if workers == 1:
        return dict(traiter(p) for p in fichiers_bytes)

    out = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for doc, txt in pool.map(traiter, fichiers_bytes):
            out[doc] = txt
    return out


# =========================
# FUSION
# =========================

def fusionner_extractions(extractions: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:

    fusion = {
        "cin": None,
        "revenu_mensuel": None,
        "loyer_mensuel": None,
        "montant_devis": None,
        "anciennete_emploi_mois": None,
    }

    revenus = []

    for doc, data in extractions.items():
        champ = get_target_field(doc)
        val = data.get("valeur")

        if champ == "revenu_mensuel":
            v = convertir_float(val)
            if v:
                revenus.append(v)
            continue

        if champ == "anciennete_emploi_mois":
            v = convertir_int(val)
            if v is not None and fusion["anciennete_emploi_mois"] is None:
                fusion["anciennete_emploi_mois"] = v
            continue

        if champ == "montant_devis":
            v = convertir_float(val)
            if v is not None:
                fusion["montant_devis"] = v
            continue

        if champ in fusion and fusion[champ] is None:
            fusion[champ] = val

    if revenus:
        fusion["revenu_mensuel"] = round(sum(revenus) / len(revenus), 2)

    return fusion


# =========================
# COHERENCE METIER
# =========================

def verifier_coherence_metier(
    declared,
    extracted,
    cin_reference
):

    anomalies = []
    corrections = {}

    # ================= CIN =================
    cin_decl = normaliser_cin(declared.get("cin"))
    cin_ext = normaliser_cin(extracted.get("cin"))

    if cin_reference and cin_ext and cin_ext != cin_reference:
        return erreur_bloquante(
            "COH_CIN_MISMATCH",
            "CIN incohérent avec la référence"
        )

    if cin_decl and cin_ext and cin_decl != cin_ext:
        anomalies.append({
            "code": "COH_CIN_DIFF",
            "niveau": "BLOQUANT",
            "message": "CIN différent"
        })
        corrections["cin"] = cin_ext

    # ================= REVENU =================
    d = convertir_float(declared.get("revenu_mensuel"))
    e = convertir_float(extracted.get("revenu_mensuel"))

    if d and e and abs(d - e) / max(d, 1) > 0.2:
        anomalies.append({
            "code": "COH_REVENU_DIFF",
            "niveau": "ALERTE",
            "message": "Écart revenu > 20%"
        })
        corrections["revenu_mensuel"] = e

    # ================= LOYER =================
    d = convertir_float(declared.get("loyer_mensuel"))
    e = convertir_float(extracted.get("loyer_mensuel"))

    if d and e and abs(d - e) / max(d, 1) > 0.2:
        anomalies.append({
            "code": "COH_LOYER_DIFF",
            "niveau": "ALERTE",
            "message": "Écart loyer > 20%"
        })
        corrections["loyer_mensuel"] = e

    # ================= ANCIENNETE =================
    d = convertir_int(declared.get("anciennete_emploi_mois"))
    e = convertir_int(extracted.get("anciennete_emploi_mois"))

    if d and e and abs(d - e) > 6:
        anomalies.append({
            "code": "COH_ANCIENNETE_DIFF",
            "niveau": "ALERTE",
            "message": "Ancienneté différente"
        })
        corrections["anciennete_emploi_mois"] = e

    # ================= DEVIS =================
    d = convertir_float(declared.get("montant"))
    e = convertir_float(extracted.get("montant_devis"))

    if d and e and abs(d - e) / max(d, 1) > 0.1:
        anomalies.append({
            "code": "COH_DEVIS_DIFF",
            "niveau": "ALERTE",
            "message": "Montant devis différent"
        })
        corrections["montant"] = e

    # ================= RESULT =================
    if anomalies:
        return {
            "score_coherence": max(0, 100 - len(anomalies) * 10),
            "anomalies": anomalies,
            "corrections": corrections
        }

    return {
        "score_coherence": 100,
        "anomalies": [],
        "corrections": {}
    }


# =========================
# REGLES DOCUMENTS
# =========================

def calculer_documents_requis(data: Dict[str, Any]) -> List[str]:
    base = {
        "cin",
        "fiche_paie_m1",
        "fiche_paie_m2",
        "fiche_paie_m3",
        "attestation_travail",
    }

    montant = convertir_float(data.get("montant"))
    if montant and montant > 10000:
        base.add("devis")

    if data.get("aUnLoyer"):
        base.add("justificatif_loyer")

    return sorted(base)