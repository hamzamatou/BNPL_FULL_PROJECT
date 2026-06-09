"""Features + sens SHAP (hausse/baisse PD) pour un profil prescoring."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import joblib
import pandas as pd

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / "service-coherence-ocr"))

from test_rest_dataset import preprocess_full  # noqa: E402

MODEL = ROOT / "bnpl_model_production.pkl"

PROFILE = {
    "revenu_mensuel_net": 1100.0,
    "revenu_annuel": 13800.0,
    "charges_mensuelles_totales": 620.0,
    "montant_demande": 2700.0,
    "nbr_mois_remboursement": 6.0,
    "anciennete_emploi_mois": 8.0,
    "type_contrat": "CDD",
}

LABELS = {
    "buffer_financier": "Marge financiere (buffer)",
    "stress_financier": "Stress financier",
    "reste_a_vivre": "Reste a vivre",
    "mensualite_bnpl": "Mensualite BNPL",
    "nbr_mois_remboursement": "Duree remboursement (mois)",
    "log_montant": "Montant demande (log)",
    "revenu_mensuel_net": "Revenu mensuel net",
    "charges_mensuelles_totales": "Charges mensuelles",
    "anciennete_emploi_mois": "Anciennete emploi (mois)",
    "taux_endettement_global": "Taux endettement global",
    "ratio_mensualite_revenu": "Ratio mensualite / revenu",
    "type_contrat": "Type de contrat",
}


def main() -> None:
    meta = joblib.load(MODEL)
    df = pd.DataFrame([PROFILE])
    X, _ = preprocess_full(df, meta)
    model = meta["model"]
    p = float(model.predict_proba(X)[0, 1])

    from app.services.prescoring_explanations import compute_shap_values_dict  # noqa: E402

    feats = list(meta.get("features") or X.columns)
    shap_dict = compute_shap_values_dict(
        model, X, feats, bundle_explainer=meta.get("shap_explainer")
    ) or {}

    rows = []
    for name in feats:
        val = float(X[name].iloc[0]) if name in X.columns else None
        shap_v = shap_dict.get(name)
        if shap_v is None or abs(shap_v) < 1e-12:
            effet = "neutre / negligeable"
            shap_f = None
        elif shap_v > 0:
            effet = "AUGMENTE la PD"
            shap_f = round(shap_v, 6)
        else:
            effet = "DIMINUE la PD"
            shap_f = round(shap_v, 6)
        rows.append(
            {
                "feature": name,
                "libelle": LABELS.get(name, name.replace("_", " ")),
                "valeur_apres_preprocess": round(val, 4) if val is not None else None,
                "shap": shap_f,
                "effet_sur_pd": effet,
            }
        )

    rows.sort(key=lambda r: abs(r["shap"] or 0), reverse=True)

    out = {
        "profil": PROFILE,
        "pd_pct": round(p * 100, 2),
        "seuil_pct": round(float(meta.get("threshold", 0.5)) * 100, 2),
        "features": rows,
    }
    text = json.dumps(out, ensure_ascii=False, indent=2)
    Path(ROOT / "_explain_profile_result.json").write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
