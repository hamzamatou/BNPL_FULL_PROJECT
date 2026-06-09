"""Genere des cas prescoring PD elevee (stdout)."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "service-coherence-ocr"))
sys.path.insert(0, str(ROOT / "bnpl-data-pipeline"))

from app.services.prescoring_service import prescore_dossier  # noqa: E402

CASES: list[tuple[str, dict]] = [
    (
        "CDD faible revenu (demo pipeline)",
        {
            "revenu_mensuel_net": 900,
            "revenu_annuel": 10800,
            "charges_mensuelles_totales": 620,
            "montant_demande": 2500,
            "nbr_mois_remboursement": 6,
            "anciennete_emploi_mois": 8,
            "type_contrat": "CDD",
        },
    ),
    (
        "Charges superieures au revenu",
        {
            "revenu_mensuel_net": 1200,
            "revenu_annuel": 14400,
            "charges_mensuelles_totales": 2250,
            "montant_demande": 3000,
            "nbr_mois_remboursement": 12,
            "anciennete_emploi_mois": 48,
            "type_contrat": "CDI",
        },
    ),
    (
        "CDD anciennete 3 mois, pret 4000 / 6 mois",
        {
            "revenu_mensuel_net": 1200,
            "revenu_annuel": 14400,
            "charges_mensuelles_totales": 750,
            "montant_demande": 4000,
            "nbr_mois_remboursement": 6,
            "anciennete_emploi_mois": 3,
            "type_contrat": "CDD",
        },
    ),
    (
        "Revenu 800 DT, montant 5000",
        {
            "revenu_mensuel_net": 800,
            "revenu_annuel": 9600,
            "charges_mensuelles_totales": 400,
            "montant_demande": 5000,
            "nbr_mois_remboursement": 12,
            "anciennete_emploi_mois": 6,
            "type_contrat": "CDD",
        },
    ),
    (
        "CDI endettement eleve",
        {
            "revenu_mensuel_net": 1500,
            "revenu_annuel": 18000,
            "charges_mensuelles_totales": 1100,
            "montant_demande": 6000,
            "nbr_mois_remboursement": 24,
            "anciennete_emploi_mois": 12,
            "type_contrat": "CDI",
        },
    ),
    (
        "Controle faible PD",
        {
            "revenu_mensuel_net": 2200,
            "revenu_annuel": 26400,
            "charges_mensuelles_totales": 750,
            "montant_demande": 3000,
            "nbr_mois_remboursement": 12,
            "anciennete_emploi_mois": 48,
            "type_contrat": "CDI",
        },
    ),
]

if __name__ == "__main__":
    out = Path(__file__).with_name("_sample_pd_cases_result.txt")
    lines: list[str] = []
    for name, body in CASES:
        r = prescore_dossier(body)
        q = "&".join(f"{k}={body[k]}" for k in body)
        lines.append(f"=== {name} ===")
        lines.append(
            f"PD%={r['pd_pct']} score={r['score']} zone={r['zone']['code']} defaut={r['defaut']}"
        )
        lines.append(f"http://localhost:8090/prescoring/prescore?{q}")
        lines.append("")
    text = "\n".join(lines)
    out.write_text(text, encoding="utf-8")
    print(text)
