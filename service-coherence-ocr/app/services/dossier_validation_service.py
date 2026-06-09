"""
Validation dossier : cohérence OCR puis recommandations si anomalies[] vide.
"""
from __future__ import annotations

from typing import Any, Dict, List

from app.services.coherence_service import verifier_coherence_dossier
from app.services.service_recommendation import DossierFinancier, generer_recommandations


def _build_dossier_financier(declared: Dict[str, Any]) -> DossierFinancier:
    revenu = float(declared.get("revenu_mensuel") or declared.get("revenu_mensuel_net") or 0)
    loyer = float(declared.get("loyer_mensuel") or 0)
    mensualites = float(declared.get("mensualites_credits") or declared.get("mensualites_credits_existants") or 0)
    autres = float(declared.get("autres_charges_fixes") or 0)
    enfants = int(declared.get("nombre_enfants") or 0)
    charges_enfants = max(0, enfants) * 300
    charges_total = loyer + mensualites + autres + charges_enfants

    return DossierFinancier(
        revenu_mensuel_net=revenu,
        charges_mensuelles_totales=charges_total,
        mensualites_credits_existants=mensualites,
        encours_credits=float(declared.get("encours_credits") or 0),
        anciennete_emploi_mois=int(declared.get("anciennete_emploi_mois") or 0),
        montant_financement=float(declared.get("montant") or declared.get("montant_financement") or 0),
        duree_mois=int(declared.get("duree_mois") or 24),
    )


def valider_dossier_et_recommander(
    donnees_declarees: Dict[str, Any],
    fichiers,
) -> Dict[str, Any]:
    """
    Retourne toujours HTTP 200 côté route :
      - anomalies non vide → recommandations = []
      - anomalies vide     → recommandations remplies
    """
    coherence = verifier_coherence_dossier(donnees_declarees, fichiers)

    if coherence.get("documents_manquants"):
        return {
            "anomalies": [
                {
                    "code": "DOCS_MANQUANTS",
                    "niveau": "BLOQUANT",
                    "message": "Documents manquants : "
                    + ", ".join(coherence.get("documents_manquants") or []),
                }
            ],
            "corrections": coherence.get("corrections") or {},
            "recommandations": [],
        }

    anomalies: List[Dict[str, Any]] = coherence.get("anomalies") or []
    corrections = coherence.get("corrections") or {}

    if anomalies:
        return {
            "anomalies": anomalies,
            "corrections": corrections,
            "recommandations": [],
        }

    reco_result = generer_recommandations(_build_dossier_financier(donnees_declarees))
    reco_list = list(reco_result.recommandations or [])

    return {
        "anomalies": [],
        "corrections": corrections,
        "recommandations": reco_list,
    }
