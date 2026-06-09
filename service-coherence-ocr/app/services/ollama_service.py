import json
import re
from calendar import monthrange
from datetime import date
from typing import Any, Dict

import requests

from app.config import Settings

DEFAULT_DOC_RESULT = {"champ_cible": None, "valeur": None}
_ollama_session = requests.Session()

_EASTERN_ARABIC_DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
_MONEY_DOC_TYPES = {"fiche_paie_m1", "fiche_paie_m2", "fiche_paie_m3", "justificatif_loyer", "devis"}


def get_target_field(doc_type: str) -> str:
    if doc_type == "cin":
        return "cin"
    if doc_type in {"fiche_paie_m1", "fiche_paie_m2", "fiche_paie_m3"}:
        return "revenu_mensuel"
    if doc_type == "justificatif_loyer":
        return "loyer_mensuel"
    if doc_type == "devis":
        return "montant_devis"
    if doc_type == "attestation_travail":
        return "anciennete_emploi_mois"
    return "revenu_mensuel"


def _doc_type_guidance(doc_type: str) -> str:
    mapping = {
        "cin": "CIN tunisien 8 chiffres; ignorer autres numeros (telephone, dates).",
        "fiche_paie_m1": "Bulletin heterogene: tableaux OCR bruités; prioriser le NET mensuel.",
        "fiche_paie_m2": "Bulletin heterogene: tableaux OCR bruités; prioriser le NET mensuel.",
        "fiche_paie_m3": "Bulletin heterogene: tableaux OCR bruités; prioriser le NET mensuel.",
        "attestation_travail": (
            "Attestation: extraire la DATE D'EMBAUCHE (référence) si présente; "
            "sinon l'ancienneté explicite en mois. Ne pas confondre avec le salaire."
        ),
        "justificatif_loyer": "Quittance / contrat / mixte FR+AR: uniquement le LOYER MENSUEL (montant par mois).",
        "devis": "Devis / proforma: extraire le MONTANT TOTAL du financement (TTC / prix global), pas des charges mensuelles.",
    }
    return mapping.get(doc_type, "Document administratif; extraire la valeur demandee avec prudence.")


def _normalize_digits_eastern(s: str) -> str:
    return (s or "").translate(_EASTERN_ARABIC_DIGITS)


def _clean_number_like(value: str) -> str:
    v = _normalize_digits_eastern(value).replace(" ", "")
    v = re.sub(r"[^\d,.\-]", "", v)
    if v.count(",") > 0 and v.count(".") == 0:
        v = v.replace(",", ".")
    return v


def _normalize_money_like(value: Any) -> str | None:
    if value is None:
        return None
    raw = _normalize_digits_eastern(str(value)).strip()
    if not raw:
        return None
    cleaned = re.sub(r"[^\d,.\-]", "", raw)
    if not cleaned:
        return None
    if "," in cleaned and "." in cleaned:
        last_comma = cleaned.rfind(",")
        last_dot = cleaned.rfind(".")
        decimal_sep = "," if last_comma > last_dot else "."
        thousands_sep = "." if decimal_sep == "," else ","
        cleaned = cleaned.replace(thousands_sep, "")
        if decimal_sep == ",":
            cleaned = cleaned.replace(",", ".")
    elif "," in cleaned:
        cleaned = cleaned.replace(",", ".")
    if cleaned.count(".") > 1:
        parts = cleaned.split(".")
        cleaned = "".join(parts[:-1]) + "." + parts[-1]
    try:
        parsed = float(cleaned)
    except ValueError:
        return None
    if parsed.is_integer():
        i = int(parsed)
        if abs(i) >= 100000 and i % 1000 == 0:
            parsed = i / 1000.0
    return f"{parsed:.3f}".rstrip("0").rstrip(".")


def _normalize_value_for_doc(value: Any, doc_type: str) -> Any:
    if value in (None, ""):
        return None
    if doc_type in _MONEY_DOC_TYPES:
        return _normalize_money_like(value)
    if doc_type == "attestation_travail":
        try:
            return str(max(0, int(float(str(value).replace(",", ".")))))
        except (TypeError, ValueError):
            return None
    if doc_type == "cin":
        d = "".join(ch for ch in _normalize_digits_eastern(str(value)) if ch.isdigit())
        return d if len(d) == 8 else None
    return str(value).strip()


def _first_number_after_keywords(text: str, patterns: list[str]) -> str | None:
    for p in patterns:
        m = re.search(p, text, flags=re.IGNORECASE)
        if m:
            return _clean_number_like(m.group(1))
    return None


def _extract_net_a_payer_amount(text: str) -> str | None:
    return _first_number_after_keywords(
        text or "",
        [
            r"net\s*a\s*payer(?:\s*h(?:ors)?\s*arrondi)?\s*[:\-]?\s*([0-9][0-9\s.,]*)",
            r"net\s*a\s*payer(?:\s*avant\s*deductions)?\s*[:\-]?\s*([0-9][0-9\s.,]*)",
            r"net\s*à\s*payer\s*[:\-]?\s*([0-9][0-9\s.,]*)",
        ],
    )


def _fallback_value_from_ocr(ocr_text: str, doc_type: str) -> str | None:
    text = (ocr_text or "").strip()
    if not text:
        return None

    if doc_type == "cin":
        t = _normalize_digits_eastern(text)
        m = re.search(r"\b(\d{8})\b", t)
        return m.group(1) if m else None

    if doc_type == "attestation_travail":
        mois = _anciennete_mois_from_attestation_text(text)
        return str(mois) if mois is not None else None

    if doc_type in {"fiche_paie_m1", "fiche_paie_m2", "fiche_paie_m3"}:
        net = _extract_net_a_payer_amount(text)
        if net:
            return net
        return _first_number_after_keywords(
            text,
            [
                r"salaire\s*mensuel\s*net\s*[:\-]?\s*([0-9][0-9\s.,]*)",
                r"salaire\s*net\s*[:\-]?\s*([0-9][0-9\s.,]*)",
                r"revenu\s*mensuel\s*[:\-]?\s*([0-9][0-9\s.,]*)",
            ],
        )

    if doc_type == "justificatif_loyer":
        return _first_number_after_keywords(
            text,
            [
                r"loyer\s*mensuel\s*[:\-]?\s*([0-9][0-9\s.,]*)",
                r"معلوم\s*الكراء\s*[:\-]?\s*([0-9][0-9\s.,]*)",
            ],
        )

    if doc_type == "devis":
        return _first_number_after_keywords(
            text,
            [
                r"montant\s*total(?:\s*ttc)?\s*[:\-]?\s*([0-9][0-9\s.,]*)",
                r"total\s*[:\-]?\s*([0-9][0-9\s.,]*)",
                r"facture\s*[:\-]?\s*([0-9][0-9\s.,]*)",
            ],
        )

    return None


def _months_between_dates(start: date, end: date) -> int:
    """Nombre de mois complets entre date d'embauche et date de référence (aujourd'hui)."""
    if start > end:
        return 0
    months = (end.year - start.year) * 12 + (end.month - start.month)
    if end.day < start.day:
        months -= 1
    return max(0, months)


def _parse_date_embauche(value: Any) -> date | None:
    """Parse YYYY-MM-DD ou JJ/MM/AAAA (et variantes)."""
    if value is None:
        return None
    raw = _normalize_digits_eastern(str(value).strip())
    if not raw:
        return None

    m = re.match(r"^(\d{4})-(\d{1,2})-(\d{1,2})$", raw)
    if m:
        y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return _safe_date(y, mo, d)

    for pat in (
        r"(\d{1,2})[./\-](\d{1,2})[./\-](\d{4})",
        r"(\d{1,2})[./\-](\d{1,2})[./\-](\d{2})",
    ):
        m = re.search(pat, raw)
        if not m:
            continue
        d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if y < 100:
            y += 2000 if y < 50 else 1900
        return _safe_date(y, mo, d)
    return None


def _safe_date(year: int, month: int, day: int) -> date | None:
    try:
        if month < 1 or month > 12:
            return None
        last = monthrange(year, month)[1]
        day = min(max(1, day), last)
        return date(year, month, day)
    except (ValueError, TypeError):
        return None


def _extract_date_embauche_from_ocr(ocr_text: str) -> date | None:
    """Repère une date d'embauche / « depuis le … » dans le texte OCR."""
    t = _normalize_digits_eastern(ocr_text or "")
    if not t:
        return None

    contextual = [
        r"(?:date\s*d['’]?\s*embauche|date\s*d['’]?\s*entree\s+en\s+fonction|date\s*de\s*prise\s+de\s+service|"
        r"embauche\s+le|depuis\s+le|en\s+service\s+depuis\s+le|nomme\s+le|à\s+partir\s+du)\s*[:\s]*"
        r"(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})",
        r"(?:date\s*d['’]?\s*embauche|depuis\s+le)\s*[:\s]*(\d{4})-(\d{1,2})-(\d{1,2})",
    ]
    for pat in contextual:
        m = re.search(pat, t, flags=re.IGNORECASE)
        if not m:
            continue
        if len(m.groups()) == 3 and len(m.group(3)) == 4:
            d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
        elif len(m.groups()) == 3 and len(m.group(1)) == 4:
            y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        else:
            d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
            if y < 100:
                y += 2000 if y < 50 else 1900
        parsed = _safe_date(y, mo, d)
        if parsed:
            return parsed

    iso = re.search(r"\b(\d{4})-(\d{2})-(\d{2})\b", t)
    if iso:
        return _parse_date_embauche(iso.group(0))
    return None


def _anciennete_mois_from_explicit_text(ocr_text: str) -> int | None:
    t = _normalize_digits_eastern(ocr_text or "")
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*ans?\b", t, flags=re.IGNORECASE)
    if m:
        years = float(_clean_number_like(m.group(1)).replace(",", "."))
        return max(0, int(round(years * 12)))
    m = re.search(r"(\d+)\s*mois\b", t, flags=re.IGNORECASE)
    if m:
        return max(0, int(m.group(1)))
    m = re.search(
        r"anciennet[eé]\s*(?:d['’]?\s*emploi|dans\s*l['’]?emploi)?\s*[:\-]?\s*(\d+)\s*mois",
        t,
        flags=re.IGNORECASE,
    )
    if m:
        return max(0, int(m.group(1)))
    return None


def _anciennete_mois_from_attestation_text(
    ocr_text: str,
    reference_date: date | None = None,
) -> int | None:
    """
    Priorité : date d'embauche → mois jusqu'à la date système ;
    sinon ancienneté explicite (X ans / X mois).
    """
    ref = reference_date or date.today()
    hire = _extract_date_embauche_from_ocr(ocr_text)
    if hire:
        return _months_between_dates(hire, ref)
    return _anciennete_mois_from_explicit_text(ocr_text)


def _attestation_has_explicit_anciennete_signal(ocr_text: str) -> bool:
    if _extract_date_embauche_from_ocr(ocr_text):
        return True
    t = _normalize_digits_eastern((ocr_text or "").lower())
    patterns = [
        r"anciennet[eé]\s*(?:d['’]?\s*emploi|dans\s*l['’]?emploi)?\s*[:\-]?\s*\d+\s*mois",
        r"anciennet[eé]\s*(?:d['’]?\s*emploi|dans\s*l['’]?emploi)?\s*[:\-]?\s*\d+(?:[.,]\d+)?\s*ans?",
        r"(?:date\s*d['’]?\s*embauche|depuis\s+le|embauche\s+le)\b",
        r"\b\d+\s*mois\b",
        r"\b\d+(?:[.,]\d+)?\s*ans?\b",
    ]
    return any(re.search(p, t, flags=re.IGNORECASE) for p in patterns)


def _finalize_attestation_anciennete(
    parsed: Dict[str, Any] | None,
    valeur: Any,
    ocr_text: str,
    reference_date: date | None = None,
) -> str | None:
    """
    Résout anciennete_emploi_mois : date_embauche (LLM ou OCR) → différence en mois,
    sinon valeur entière si signal explicite, sinon fallback OCR.
    """
    ref = reference_date or date.today()
    data = parsed or {}

    date_embauche = _parse_date_embauche(data.get("date_embauche"))
    if date_embauche is None and valeur not in (None, ""):
        date_embauche = _parse_date_embauche(valeur)

    if date_embauche is None:
        date_embauche = _extract_date_embauche_from_ocr(ocr_text)

    if date_embauche:
        return str(_months_between_dates(date_embauche, ref))

    if valeur not in (None, ""):
        try:
            mois = max(0, int(float(str(valeur).replace(",", "."))))
            if _attestation_has_explicit_anciennete_signal(ocr_text):
                return str(mois)
        except (TypeError, ValueError):
            pass

    mois_fb = _anciennete_mois_from_attestation_text(ocr_text, reference_date=ref)
    return str(mois_fb) if mois_fb is not None else None


def _build_prompt(ocr_text: str, doc_type: str) -> str:
    target_field = get_target_field(doc_type)
    max_chars = max(500, int(Settings.OLLAMA_MAX_OCR_CHARS))
    text_for_model = (ocr_text or "")[:max_chars]

    regles_cin = """
CIN (carte d'identite tunisienne, souvent en arabe):
- Chercher le numero national a 8 chiffres (chiffres latins OU chiffres arabes orientaux ٠١٢٣٤٥٦٧٨٩).
- Libelles possibles: "CIN", "رقم بطاقة التعريف", "بطاقة التعريف الوطنية", "N°", "Numero".
- Ignorer dates (JJ/MM/AAAA), telephones, matricules employeur.
- Sortie: exactement 8 chiffres latins 0-9 sans espaces.
""".strip()

    regles_anciennete = """
ANCIENNETE emploi (attestation de travail) — priorite DATE D'EMBAUCHE:
- Chercher "date d'embauche", "depuis le", "embauche le", "en service depuis", "تاريخ التوظيف", "منذ".
- Si une date d'embauche est lisible: la mettre dans "date_embauche" (format YYYY-MM-DD), "valeur": null.
- Si seule une duree explicite est indiquee (ex. "3 ans", "36 mois"): "date_embauche": null et "valeur" = nombre entier de MOIS.
- Ne pas inventer de date ni de duree.
""".strip()

    regles_montant = """
Montant (revenu_mensuel, loyer_mensuel):
- Nombre decimal avec "." (ex: 2500 ou 2500.5), pas de symbole devise, pas de texte.
""".strip()

    regles_montant_devis = """
MONTANT sur le DEVIS / proforma (total du financement ou prix global TTC):
- Preferer le montant principal: "Total TTC", "Montant total", "Prix total", "Montant du devis".
- Eviter une petite ligne (frais, acompte) si un total global est visible.
- Sortie: nombre decimal avec "." sans devise, sans texte.
""".strip()

    if target_field == "cin":
        regles_champ = regles_cin
    elif target_field == "anciennete_emploi_mois":
        regles_champ = regles_anciennete
    elif target_field == "montant_devis":
        regles_champ = regles_montant_devis
    else:
        regles_champ = regles_montant

    json_schema = (
        f'{{ "champ_cible": "{target_field}", "date_embauche": null, "valeur": null }}'
        if target_field == "anciennete_emploi_mois"
        else f'{{ "champ_cible": "{target_field}", "valeur": null }}'
    )

    return f"""
Tu analyses des justificatifs financiers tunisiens pour un dossier BNPL.
Les documents sont HETEROGENES: mise en page variable, tableaux deformes par l'OCR, melange FR/arabe, libelles non standardises.

Ta tache: extraire UNE seule valeur pour le champ "{target_field}" a partir du texte OCR ci-dessous.
Le texte peut contenir du bruit (lignes coupees, caracteres OCR errones, en-tetes/pieds de page): ignore le hors-sujet.

Sortie OBLIGATOIRE: un seul objet JSON valide, sans markdown, sans texte avant/apres:
{json_schema}

Regles specifiques au champ demande:
{regles_champ}
- Si introuvable ou trop ambigu: "valeur": null

Synonymes / formulations frequentes (FR + AR) pour REVENU MENSUEL (net a prendre en priorite):
- "net a payer", "net à payer", "Net a Payer", "salaire net", "salaire mensuel net", "revenu mensuel", "remuneration nette"
- "صافي الراتب", "الراتب الصافي", "الأجر", "صافي الأجر", "الدخل الشهري"

Priorite si PLUSIEURS montants sur une fiche de paie (du plus fiable au secours):
1) ligne / etiquette evoquant "net a payer" / "net à payer" / "Net a Payer"
2) "salaire net" / "salaire mensuel net"
3) "salaire imposable" ou "salaire brut" seulement si aucun net clair

Synonymes LOYER MENSUEL uniquement (pas total annuel):
- "loyer mensuel", "loyer", "quittance", "mensualite loyer", "كراء", "اجرة", "معلوم الكراء", "الكراء الشهري"

Synonymes MONTANT DEVIS (total financement / bien):
- "montant total", "total TTC", "montant TTC", "prix total", "montant du devis", "المبلغ الإجمالي", "المجموع"

Contexte document (type technique): {doc_type}
Rappel specifique: {_doc_type_guidance(doc_type)}

Texte OCR:
{text_for_model}
""".strip()


def _extract_json_object(content: str) -> Dict[str, Any] | None:
    if not content:
        return None
    content = content.strip()
    try:
        data = json.loads(content)
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    match = re.search(r"\{[\s\S]*\}", content)
    if not match:
        return None
    try:
        data = json.loads(match.group(0))
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        return None
    return None


def _fast_path_regex(ocr_text: str, doc_type: str) -> Dict[str, Any] | None:
    champ_cible = get_target_field(doc_type)
    if Settings.COHERENCE_SKIP_LLM_FOR_CIN and doc_type == "cin":
        fb_cin = _fallback_value_from_ocr(ocr_text, "cin")
        if fb_cin and len(fb_cin) == 8 and fb_cin.isdigit():
            return {"champ_cible": champ_cible, "valeur": _normalize_value_for_doc(fb_cin, doc_type)}
    if Settings.COHERENCE_REGEX_FIRST:
        fb = _fallback_value_from_ocr(ocr_text, doc_type)
        if fb not in (None, ""):
            return {"champ_cible": champ_cible, "valeur": _normalize_value_for_doc(fb, doc_type)}
    return None


def _build_batch_prompt(ocr_by_doc: Dict[str, str], doc_types: list[str]) -> str:
    lines: list[str] = []
    n = max(1, len(doc_types))
    per_doc = max(500, int(Settings.OLLAMA_MAX_OCR_CHARS) // n)
    for dt in doc_types:
        target = get_target_field(dt)
        guide = _doc_type_guidance(dt)
        txt = (ocr_by_doc.get(dt) or "").strip()[:per_doc]
        lines.append(f"### DOCUMENT type_technique={dt}")
        lines.append(f"Champ a remplir pour ce document: {target}")
        lines.append(f"Consigne: {guide}")
        lines.append("Texte_OCR:")
        lines.append(txt if txt else "(vide)")
        lines.append("")
    doc_keys_json = ", ".join(f'"{dt}"' for dt in doc_types)
    return f"""
Tu analyses un dossier BNPL (justificatifs tunisiens). Plusieurs textes OCR sont fournis, chacun precede de son type_technique.

Tache: pour CHAQUE type_technique liste, extraire UNE valeur pour le champ indique (voir consigne par bloc).

Sortie OBLIGATOIRE: un seul objet JSON valide (sans markdown, sans texte hors JSON) dont les cles EXACTES sont les types techniques, dans cet ordre logique: {doc_keys_json}

Chaque valeur doit etre un objet de la forme:
{{ "champ_cible": "<nom du champ>", "valeur": <string ou nombre ou null> }}

Exemple de forme (cles a adapter aux types reels):
{{
  "cin": {{ "champ_cible": "cin", "valeur": "12345678" }},
  "fiche_paie_m1": {{ "champ_cible": "revenu_mensuel", "valeur": "2500.5" }}
}}

Regles globales:
- CIN: exactement 8 chiffres latins (convertir chiffres arabes orientaux si presents).
- Montants: nombre avec "." decimal, pas de devise, pas de texte autour; null si introuvable.
- Attestation / anciennete: preferer "date_embauche" (YYYY-MM-DD) si presente, sinon "valeur" en MOIS entier; le service calcule les mois depuis la date.
- Si un bloc OCR est trop bruite ou ambigu: "valeur": null pour ce document.

Blocs fournis:

{chr(10).join(lines)}
""".strip()


def _parse_batch_response(content: str, doc_types: list[str]) -> Dict[str, Dict[str, Any]]:
    data = _extract_json_object(content)
    if not data:
        return {}
    out: Dict[str, Dict[str, Any]] = {}
    for dt in doc_types:
        raw = data.get(dt)
        if isinstance(raw, dict):
            out[dt] = {
                "champ_cible": raw.get("champ_cible") or get_target_field(dt),
                "valeur": raw.get("valeur"),
                "date_embauche": raw.get("date_embauche"),
            }
        elif isinstance(raw, str):
            out[dt] = {"champ_cible": get_target_field(dt), "valeur": raw}
    return out


def _try_batch_llm(ocr_by_doc: Dict[str, str], doc_types: list[str]) -> Dict[str, Dict[str, Any]]:
    if len(doc_types) < 2:
        return {}
    prompt = _build_batch_prompt(ocr_by_doc, doc_types)
    n = len(doc_types)
    timeout = Settings.OLLAMA_TIMEOUT_BATCH_SEC
    if timeout <= 0:
        timeout = max(Settings.OLLAMA_TIMEOUT_SEC, min(300, Settings.OLLAMA_TIMEOUT_SEC * n))
    num_pred = Settings.OLLAMA_NUM_PREDICT_BATCH if Settings.OLLAMA_NUM_PREDICT_BATCH > 0 else 2048
    payload = {
        "model": Settings.OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": 0.1, "num_predict": num_pred},
    }
    resp = _ollama_session.post(Settings.OLLAMA_URL, json=payload, timeout=timeout)
    resp.raise_for_status()
    return _parse_batch_response(resp.json().get("response", "").strip(), doc_types)


def extract_structured_fields_for_dossier(
    ocr_by_doc: Dict[str, str],
    doc_types_ordered: list[str],
) -> Dict[str, Dict[str, Any]]:
    out: Dict[str, Dict[str, Any]] = {}
    need_llm: list[str] = []
    for dt in doc_types_ordered:
        if dt not in ocr_by_doc:
            continue
        ocr = ocr_by_doc[dt]
        fast = _fast_path_regex(ocr, dt)
        if fast is not None:
            out[dt] = fast
            continue
        need_llm.append(dt)

    if not need_llm:
        return out

    if not Settings.COHERENCE_LLM_BATCH or len(need_llm) == 1:
        for dt in need_llm:
            out[dt] = extract_structured_fields(ocr_by_doc[dt], dt)
        return out

    batch_result: Dict[str, Dict[str, Any]] = {}
    try:
        batch_result = _try_batch_llm(ocr_by_doc, need_llm)
    except Exception:
        batch_result = {}

    for dt in need_llm:
        row = batch_result.get(dt)
        if isinstance(row, dict):
            valeur = row.get("valeur")
            if isinstance(valeur, str):
                valeur = valeur.strip() or None
            if dt in {"fiche_paie_m1", "fiche_paie_m2", "fiche_paie_m3"}:
                net = _extract_net_a_payer_amount(ocr_by_doc.get(dt, ""))
                if net:
                    valeur = net
            if dt == "attestation_travail":
                valeur = _finalize_attestation_anciennete(
                    row,
                    row.get("valeur"),
                    ocr_by_doc.get(dt, ""),
                )
            else:
                valeur = _normalize_value_for_doc(valeur, dt)
            if valeur not in (None, ""):
                out[dt] = {"champ_cible": row.get("champ_cible") or get_target_field(dt), "valeur": valeur}
                continue
        out[dt] = extract_structured_fields(ocr_by_doc[dt], dt)
    return out


def extract_structured_fields(ocr_text: str, doc_type: str):
    champ_cible = get_target_field(doc_type)
    fast = _fast_path_regex(ocr_text, doc_type)
    if fast is not None:
        return fast

    payload = {
        "model": Settings.OLLAMA_MODEL,
        "prompt": _build_prompt(ocr_text, doc_type),
        "stream": False,
        "options": {
            "temperature": 0.1,
            "num_predict": Settings.OLLAMA_NUM_PREDICT if Settings.OLLAMA_NUM_PREDICT > 0 else 512,
        },
    }
    resp = _ollama_session.post(Settings.OLLAMA_URL, json=payload, timeout=Settings.OLLAMA_TIMEOUT_SEC)
    resp.raise_for_status()
    content = resp.json().get("response", "").strip()
    data = _extract_json_object(content)
    if data is not None:
        if doc_type == "attestation_travail":
            valeur = _finalize_attestation_anciennete(data, data.get("valeur"), ocr_text)
            return {"champ_cible": data.get("champ_cible") or champ_cible, "valeur": valeur}

        valeur = data.get("valeur")
        if valeur in (None, ""):
            valeur = _fallback_value_from_ocr(ocr_text, doc_type)
        if doc_type in {"fiche_paie_m1", "fiche_paie_m2", "fiche_paie_m3"}:
            net = _extract_net_a_payer_amount(ocr_text)
            if net:
                valeur = net
        valeur = _normalize_value_for_doc(valeur, doc_type)
        return {"champ_cible": data.get("champ_cible") or champ_cible, "valeur": valeur}

    fallback = DEFAULT_DOC_RESULT.copy()
    fallback["champ_cible"] = champ_cible
    if doc_type == "attestation_travail":
        fallback["valeur"] = _finalize_attestation_anciennete(None, None, ocr_text)
    else:
        fallback["valeur"] = _normalize_value_for_doc(_fallback_value_from_ocr(ocr_text, doc_type), doc_type)
    return fallback
    # ═══════════════════════════════════════════════════════════════════════════════
# RECOMMANDATIONS IA — à coller à la fin du fichier ollama existant
# ═══════════════════════════════════════════════════════════════════════════════

def _build_recommendation_prompt(
    revenu_mensuel_net: float,
    charges_mensuelles_totales: float,
    mensualites_credits_existants: float,
    encours_credits: float,
    anciennete_emploi_mois: int,
    montant_financement: float,
    duree_mois: int,
    # valeurs pré-calculées transmises par le service
    revenu_disponible: float,
    plafond_bnpl: float,
    mensualite_bnpl: float,
    conforme: bool,
    montant_max: float | None,
    duree_min: int | None,
    score_solvabilite: str,
) -> str:
    """
    Construit le prompt Ollama pour la génération de recommandations BNPL.
    Les valeurs financières clés sont pré-calculées par service_recommendation.py
    et injectées ici — le LLM ne recalcule rien, il rédige uniquement.
    """
    statut = "CONFORME" if conforme else "NON CONFORME"

    if conforme:
        note = (
            f"Mensualité BNPL ({mensualite_bnpl} TND) ≤ plafond ({plafond_bnpl} TND). "
            "Aucun ajustement nécessaire."
        )
    elif duree_min is not None and duree_min > 0:
        note = (
            f"Mensualité BNPL ({mensualite_bnpl} TND) > plafond BCT ({plafond_bnpl} TND/mois). "
            f"Option 1 : réduire le montant à {montant_max} TND sur {duree_mois} mois. "
            f"Option 2 : allonger la durée à au moins {duree_min} mois pour garder "
            f"{montant_financement} TND."
        )
    else:
        note = (
            f"Mensualité BNPL ({mensualite_bnpl} TND) > plafond BCT ({plafond_bnpl} TND/mois). "
            f"Les mensualités de crédits existants absorbent déjà tout ou partie du "
            f"plafond légal : priorité à réduire le montant demandé ({montant_financement} TND). "
            f"Montant max indicatif pour la durée actuelle ({duree_mois} mois) : {montant_max} TND."
        )

    duree_min_json = "null"
    if not conforme and duree_min is not None and duree_min > 0:
        duree_min_json = str(duree_min)

    return f"""
Tu es un conseiller financier expert en crédit BNPL pour le marché tunisien.
Tu génères des recommandations destinées au COMMERÇANT pour maximiser
les chances d'acceptation bancaire du dossier de son client.

=== DONNÉES DU DOSSIER ===
Revenu mensuel net                    : {revenu_mensuel_net} TND
Charges mensuelles totales            : {charges_mensuelles_totales} TND
  (loyer + enfants à 300 TND/enfant + autres charges fixes)
Mensualités crédits existants         : {mensualites_credits_existants} TND
Encours crédits restants              : {encours_credits} TND
Ancienneté emploi                     : {anciennete_emploi_mois} mois

=== CALCUL RÈGLE 40% BCT (taux d'endettement) ===
Définition : (mensualités crédits existants + mensualité BNPL) / revenu net mensuel ≤ 40%

Plafond TOTAL mensuel pour tous les crédits :
  {revenu_mensuel_net} × 40% = {round(0.4 * float(revenu_mensuel_net), 3)} TND/mois

Moins mensualités des crédits déjà engagées :
  {round(0.4 * float(revenu_mensuel_net), 3)} - {mensualites_credits_existants}
  = mensualité BNPL MAX autorisée = {plafond_bnpl} TND/mois

Revenu « disponible » informatif (revenu net − mensualités crédits existants uniquement)
  (= ce qui subsiste après remboursements crédits, avant charges vie courante : loyer etc.) :
  {revenu_disponible} TND — ce n'est PAS la base du plafond BCT ci-dessus.

=== DEMANDE BNPL ===
Montant demandé                       : {montant_financement} TND
Durée                                 : {duree_mois} mois
Mensualité BNPL calculée              : {mensualite_bnpl} TND
Statut règle 40%                      : {statut}
Note                                  : {note}
=== TÂCHE ===
Génère 1 à 3 recommandations concrètes pour le commerçant, UNIQUEMENT sur le financement BNPL.

Règle absolue — alignement BCT (ne pas recalculer ces champs, décrit uniquement) :
  (mensualites_credits_existants + mensualite_bnpl) / revenu_mensuel_net ≤ 40%
  plafond_mensualité_BNPL = max(0, 0.40 × revenu_mensuel_net - mensualites_credits_existants)
  mensualite_bnpl = montant_financement / duree_mois  doit être ≤ plafond_mensualité_BNPL
  Ce plafond n'utilise PAS « 40 % du revenu après crédits ».

Types d'actions AUTORISÉS (et seulement ceux-ci) :
  1) Réduire le montant du financement demandé (indiquer un montant cible en TND si possible).
  2) Allonger la durée de remboursement en mois (indiquer un nombre de mois cible).

INTERDIT dans "recommandations" :
  - Demander des documents, pièces justificatives, fiches de paie, attestations, CIN, devis, loyer.
  - Parler d'OCR, de scans, de pièces manquantes ou supplémentaires à joindre.
  - Tout conseil hors baisse de montant ou allongement de durée.

Sortie OBLIGATOIRE : un seul objet JSON valide, sans markdown, sans texte avant/après :
{{
  "recommandations": [
    "<recommandation 1>",
    "<recommandation 2>"
  ]
}}

Règles de rédaction :
- Langue : français uniquement.
- Ton : professionnel, bienveillant, adapté au commerçant tunisien.
- 1 seule action précise par recommandation (montant OU durée, jamais les deux dans la même phrase).
- Si NON CONFORME : proposer explicitement soit un montant réduit (TND), soit une durée plus longue (mois),
  en vous appuyant sur montant_max_acceptable et duree_minimale_mois ci-dessus. Pas d'autre levier.
- Si CONFORME : exactement UNE recommandation dans "recommandations", qui DOIT commencer par
  « Demande conforme : » et préciser que le montant et la durée respectent le plafond 40 % BCT,
  qu'aucun ajustement n'est nécessaire, et le score de solvabilité ({score_solvabilite}).
  Exemple : "Demande conforme : le montant et la durée respectent le plafond d'endettement (règle 40 % BCT). Aucun ajustement nécessaire. Score de solvabilité : Bon."
- Si NON CONFORME : 1 à 3 recommandations d'ajustement (montant ou durée uniquement).
""".strip()


def call_ollama_recommendation(
    revenu_mensuel_net: float,
    charges_mensuelles_totales: float,
    mensualites_credits_existants: float,
    encours_credits: float,
    anciennete_emploi_mois: int,
    montant_financement: float,
    duree_mois: int,
    revenu_disponible: float,
    plafond_bnpl: float,
    mensualite_bnpl: float,
    conforme: bool,
    montant_max: float | None,
    duree_min: int | None,
    score_solvabilite: str,
) -> dict:
    """
    Appelle Ollama pour générer les recommandations BNPL.
    Retourne le dict JSON parsé, ou {} en cas d'échec.
    Même pattern que extract_structured_fields().
    """
    prompt = _build_recommendation_prompt(
        revenu_mensuel_net=revenu_mensuel_net,
        charges_mensuelles_totales=charges_mensuelles_totales,
        mensualites_credits_existants=mensualites_credits_existants,
        encours_credits=encours_credits,
        anciennete_emploi_mois=anciennete_emploi_mois,
        montant_financement=montant_financement,
        duree_mois=duree_mois,
        revenu_disponible=revenu_disponible,
        plafond_bnpl=plafond_bnpl,
        mensualite_bnpl=mensualite_bnpl,
        conforme=conforme,
        montant_max=montant_max,
        duree_min=duree_min,
        score_solvabilite=score_solvabilite,
    )

    payload = {
        "model": Settings.OLLAMA_MODEL,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": 0.2,
            "num_predict": Settings.OLLAMA_NUM_PREDICT if Settings.OLLAMA_NUM_PREDICT > 0 else 512,
        },
    }

    resp = _ollama_session.post(
        Settings.OLLAMA_URL,
        json=payload,
        timeout=Settings.OLLAMA_TIMEOUT_SEC,
    )
    resp.raise_for_status()
    raw = resp.json().get("response", "").strip()

    data = _extract_json_object(raw)   # réutilise la fonction déjà existante dans ce fichier
    return {"parsed": data or {}, "raw": raw}