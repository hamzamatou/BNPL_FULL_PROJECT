"""
Test manuel des modeles produits par train_multimodel.py :
  - XGBoost (pipeline + seuils dans xgboost_meta.joblib)
  - Regression logistique (pipeline)
  - Isolation Forest + StandardScaler (anomalie sur numeriques)

  python predict_manual_multimodel.py --demo
  python predict_manual_multimodel.py --revenu_mensuel_net 2200 --revenu_annuel 26400 \\
      --charges_mensuelles_totales 750 --montant_demande 3000 \\
      --nbr_mois_remboursement 12 --anciennete_emploi_mois 48 --type_contrat CDI

Puis : python train_multimodel.py (ou --quick) pour generer out/models/*.pkl
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from train_multimodel import MODELS_DIR, NUM_COLS_SCHEMA, prepare_features_from_prepare_row

REQUIRED = (
    "revenu_mensuel_net",
    "revenu_annuel",
    "charges_mensuelles_totales",
    "montant_demande",
    "nbr_mois_remboursement",
    "anciennete_emploi_mois",
    "type_contrat",
)

DEMOS: list[dict] = [
    {
        "libelle": "CDI stable",
        "revenu_mensuel_net": 2200.0,
        "revenu_annuel": 26400.0,
        "charges_mensuelles_totales": 750.0,
        "montant_demande": 3000.0,
        "nbr_mois_remboursement": 12.0,
        "anciennete_emploi_mois": 48.0,
        "type_contrat": "CDI",
    },
    {
        "libelle": "CDD tendu",
        "revenu_mensuel_net": 1200.0,
        "revenu_annuel": 14400.0,
        "charges_mensuelles_totales": 750.0,
        "montant_demande": 3000.0,
        "nbr_mois_remboursement": 8.0,
        "anciennete_emploi_mois": 2.0,
        "type_contrat": "CDD",
    },
    {
        "libelle": "Charges > revenu",
        "revenu_mensuel_net": 1200.0,
        "revenu_annuel": 14400.0,
        "charges_mensuelles_totales": 2250.0,
        "montant_demande": 3000.0,
        "nbr_mois_remboursement": 12.0,
        "anciennete_emploi_mois": 48.0,
        "type_contrat": "CDI",
    },
]


def _row_from_cli(ns: argparse.Namespace) -> dict:
    vals = {k: getattr(ns, k) for k in REQUIRED if k != "type_contrat"}
    tc = ns.type_contrat
    filled = sum(v is not None for v in vals.values()) + (1 if tc is not None else 0)
    if filled == 0:
        return {}
    if filled != 7 or tc is None:
        raise SystemExit(
            "Indiquez les 7 champs (6 nombres + --type_contrat) ou utilisez --demo / --json."
        )
    out = {k: float(vals[k]) for k in vals}
    out["type_contrat"] = str(tc)
    return out


def _rows_from_json(path: Path) -> list[tuple[str | None, dict]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(raw, dict):
        raw = [raw]
    rows: list[tuple[str | None, dict]] = []
    for i, obj in enumerate(raw):
        if not isinstance(obj, dict):
            raise ValueError(f"Element {i}: objet JSON attendu")
        label = obj.pop("libelle", None) or obj.pop("label", None)
        miss = [k for k in REQUIRED if k not in obj]
        if miss:
            raise ValueError(f"Element {i}: champs manquants {miss}")
        row = {k: float(obj[k]) if k != "type_contrat" else str(obj[k]) for k in REQUIRED}
        rows.append((label if isinstance(label, str) else None, row))
    return rows


def _alerts_metier_ligne(row_dict: dict) -> list[str]:
    """Avertissements sur les entrees brutes (complement au score ML)."""
    alerts: list[str] = []
    r = float(row_dict["revenu_mensuel_net"])
    c = float(row_dict["charges_mensuelles_totales"])
    m = float(row_dict["montant_demande"])
    n = max(float(row_dict["nbr_mois_remboursement"]), 1e-9)
    mens = m / n
    tc = str(row_dict.get("type_contrat", "")).strip().upper()
    anc = float(row_dict["anciennete_emploi_mois"])
    if c >= r:
        alerts.append("METIER: charges >= revenu mensuel.")
    if tc == "CDD" and anc < 12:
        alerts.append("METIER: CDD avec moins de 12 mois d'anciennete.")
    if float(row_dict["nbr_mois_remboursement"]) <= 6 and mens / (r + 1.0) >= 0.35:
        alerts.append("METIER: pret tres court et mensualite elevee vs revenu (souvent atypique / IF).")
    return alerts


def _load_bundle(models_dir: Path):
    paths = {
        "xgb": models_dir / "xgboost_model.pkl",
        "meta": models_dir / "xgboost_meta.joblib",
        "log": models_dir / "logistic_model.pkl",
        "iso": models_dir / "isolation_forest.pkl",
        "scaler": models_dir / "fraud_scaler.pkl",
    }
    missing = [str(p) for k, p in paths.items() if not p.is_file()]
    if missing:
        print(
            "Fichiers modele introuvables :\n  " + "\n  ".join(missing)
            + f"\n\nLancez d'abord : python train_multimodel.py --quick",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return (
        joblib.load(paths["xgb"]),
        joblib.load(paths["meta"]),
        joblib.load(paths["log"]),
        joblib.load(paths["iso"]),
        joblib.load(paths["scaler"]),
    )


def predict_rows(models_dir: Path, rows: list[tuple[str | None, dict]]) -> None:
    xgb, meta, log_m, iso, scaler = _load_bundle(models_dir)
    seuil_f1 = float(meta.get("seuil_f1", 0.5))
    seuil_metier = float(meta.get("seuil_metier", seuil_f1))

    print(f"Modeles : {models_dir.resolve()}")
    print(f"XGBoost seuils (meta) : F1-max={seuil_f1:.4f} | metier (recall)={seuil_metier:.4f}\n")

    for label, row_dict in rows:
        X = prepare_features_from_prepare_row(pd.DataFrame([row_dict]))
        title = label or "Profil (CLI)"
        print(f"  {title}")
        biz = _alerts_metier_ligne(row_dict)
        if biz:
            for b in biz:
                print(f"    ! {b}")

        px = float(xgb.predict_proba(X)[0, 1])
        pl = float(log_m.predict_proba(X)[0, 1])
        pred_x_f1 = int(px >= seuil_f1)
        pred_x_met = int(px >= seuil_metier)
        pred_log = int(pl >= 0.5)

        X_num = X[NUM_COLS_SCHEMA].astype(np.float64)
        Xs = scaler.transform(X_num)
        iso_lab = int(iso.predict(Xs)[0])
        iso_txt = "ANOMALIE (score fraude / outlier)" if iso_lab == -1 else "normal (IsolationForest)"
        if iso_lab == -1 and px < 0.25:
            print(
                "    ! Ecart: IsolationForest = anomalie mais scoring classique bas -> "
                "prioriser file / regles metier ou re-entrainer avec plus de cas atypiques."
            )

        print(f"    XGBoost  P(defaut)     : {px:.2%}")
        print(f"             classe F1    : {'defaut' if pred_x_f1 else 'non-defaut'} (>= {seuil_f1:.4f})")
        print(f"             classe metier: {'defaut' if pred_x_met else 'non-defaut'} (>= {seuil_metier:.4f})")
        print(f"    Logistic P(defaut)     : {pl:.2%} (seuil affiche 0.5)")
        print(f"             classe 0.5   : {'defaut' if pred_log else 'non-defaut'}")
        print(f"    IsolationForest      : {iso_txt}")
        print()


def main() -> int:
    parser = argparse.ArgumentParser(description="Inference manuelle multi-modeles (train_multimodel.py)")
    parser.add_argument("--models-dir", type=Path, default=MODELS_DIR, help="Dossier out/models")
    parser.add_argument("--demo", action="store_true")
    parser.add_argument("--json", type=Path, metavar="FILE")

    for k in REQUIRED:
        if k == "type_contrat":
            parser.add_argument("--type_contrat", type=str, default=None)
        else:
            parser.add_argument(f"--{k}", type=float, default=None)

    args = parser.parse_args()

    rows: list[tuple[str | None, dict]] = []
    if args.demo:
        for d in DEMOS:
            dd = dict(d)
            lib = dd.pop("libelle")
            rows.append((lib, dd))
    elif args.json is not None:
        if not args.json.is_file():
            print(f"JSON introuvable : {args.json}", file=sys.stderr)
            return 1
        try:
            rows = _rows_from_json(args.json)
        except (json.JSONDecodeError, ValueError) as e:
            print(str(e), file=sys.stderr)
            return 1
    else:
        one = _row_from_cli(args)
        if not one:
            parser.print_help()
            print("\nExemple : python predict_manual_multimodel.py --demo")
            return 0
        rows = [(None, one)]

    predict_rows(args.models_dir, rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
