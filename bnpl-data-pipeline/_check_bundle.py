"""Verification rapide du bundle prescoring."""
from __future__ import annotations

import sys
from pathlib import Path

import joblib
import pandas as pd

PACKAGE = Path(__file__).resolve().parent
sys.path.insert(0, str(PACKAGE))

MODEL = PACKAGE / "bnpl_model_production.pkl"
OUT = PACKAGE / "_check_bundle_result.txt"

REQUIRED = ("model", "threshold", "features", "encoders", "clip_lower", "clip_upper")
OPTIONAL_IF = ("isolation_forest", "if_scaler")
OPTIONAL_SHAP = ("shap_explainer",)

DEMO = {
    "revenu_mensuel_net": 2200.0,
    "revenu_annuel": 26400.0,
    "charges_mensuelles_totales": 750.0,
    "montant_demande": 3000.0,
    "nbr_mois_remboursement": 12.0,
    "anciennete_emploi_mois": 48.0,
    "type_contrat": "CDI",
}


def main() -> int:
    lines: list[str] = []
    def log(msg: str = "") -> None:
        lines.append(msg)
        print(msg)

    log(f"Fichier: {MODEL}")
    log(f"Existe: {MODEL.is_file()}")
    if not MODEL.is_file():
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    log(f"Taille (Mo): {MODEL.stat().st_size / 1024 / 1024:.2f}")

    try:
        meta = joblib.load(MODEL)
    except Exception as ex:
        log(f"ECHEC joblib.load: {type(ex).__name__}: {ex}")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    if not isinstance(meta, dict):
        log(f"ECHEC: bundle n'est pas un dict ({type(meta)})")
        OUT.write_text("\n".join(lines), encoding="utf-8")
        return 1

    log(f"Cles ({len(meta)}): {sorted(meta.keys())}")
    missing = [k for k in REQUIRED if k not in meta]
    log(f"Requis manquants: {missing or 'aucun'}")
    log(f"IF: isolation_forest={('isolation_forest' in meta)}, if_scaler={('if_scaler' in meta)}")
    log(f"SHAP: shap_explainer={('shap_explainer' in meta)}")
    if "threshold" in meta:
        log(f"Seuil: {meta['threshold']}")
    if "features" in meta:
        log(f"Nb features: {len(meta['features'])}")

    ok_prescore = True
    try:
        from test_rest_dataset import preprocess_full

        df = pd.DataFrame([DEMO])
        X, _ = preprocess_full(df, meta)
        model = meta["model"]
        p = float(model.predict_proba(X)[0, 1])
        thr = float(meta.get("threshold", 0.5))
        log(f"Test PD demo: {p:.4f} ({p*100:.2f}%) | defaut={p >= thr}")
    except Exception as ex:
        ok_prescore = False
        log(f"ECHEC inference LightGBM: {type(ex).__name__}: {ex}")

    ok_if = False
    if meta.get("isolation_forest") is not None and meta.get("if_scaler") is not None:
        try:
            iso = meta["isolation_forest"]
            sc = meta["if_scaler"]
            Xi = sc.transform(X.values)
            pred = int(iso.predict(Xi)[0])
            ss = float(iso.score_samples(Xi)[0])
            log(f"Test IF: atypique={pred == -1} predict={pred} score={ss:.4f}")
            ok_if = True
        except Exception as ex:
            log(f"ECHEC IF: {type(ex).__name__}: {ex}")
    else:
        log("IF: non teste (cles absentes)")

    ok_shap = False
    if meta.get("shap_explainer") is not None:
        try:
            import shap  # noqa: F401

            expl = meta["shap_explainer"]
            feats = list(meta.get("features") or list(X.columns))
            X_use = X.reindex(columns=feats, fill_value=0.0)
            if hasattr(expl, "shap_values"):
                raw = expl.shap_values(X_use)
            else:
                raw = expl(X_use)
            log(f"Test SHAP: OK (type sortie {type(raw).__name__})")
            ok_shap = True
        except ImportError:
            log("SHAP dans bundle mais pip install shap manquant cote runtime")
        except Exception as ex:
            log(f"ECHEC SHAP: {type(ex).__name__}: {ex}")
    else:
        log("SHAP: non teste (shap_explainer absent)")

    exploitable = not missing and ok_prescore
    log("")
    if exploitable:
        log("VERDICT: EXPLOITABLE pour prescoring (LightGBM OK).")
        if ok_if:
            log("  + Foret d'isolation OK.")
        if ok_shap:
            log("  + SHAP OK.")
        elif meta.get("shap_explainer"):
            log("  ! SHAP present dans bundle mais test echoue ou lib manquante.")
        elif "shap_explainer" not in meta:
            log("  - SHAP absent du bundle (message prescoring normal).")
        if not ok_if and meta.get("isolation_forest"):
            log("  ! IF partielle (scaler ou predict en echec).")
        elif not meta.get("isolation_forest"):
            log("  - IF absente du bundle (message prescoring normal).")
    else:
        log("VERDICT: NON EXPLOITABLE — corriger bundle ou chemin fichier.")

    OUT.write_text("\n".join(lines), encoding="utf-8")
    return 0 if exploitable else 1


if __name__ == "__main__":
    raise SystemExit(main())
