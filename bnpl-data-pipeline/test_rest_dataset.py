"""
Evaluation du modele BNPL (LightGBM calibre) sur la partie « reste » du dataset
(holdout identique au split de train_GBMlight.py).

Usage :
  python test_rest_dataset.py
  python test_rest_dataset.py --holdout test
  python test_rest_dataset.py --holdout temp
  python test_rest_dataset.py -i chemin.csv -m chemin.pkl

--holdout test  : uniquement le jeu TEST final (~15 % des lignes, jamais vu a l'entrainement LGBM)
--holdout temp  : VAL + TEST (~30 %, tout ce qui n'est pas TRAIN LGBM)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import (
    average_precision_score,
    classification_report,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split

from bnpl_fe import feature_engineering_bnpl

PACKAGE_DIR = Path(__file__).resolve().parent
DEFAULT_CSV = PACKAGE_DIR / "out" / "dataset_bnpl_tunisien_cleanV3.csv"
DEFAULT_MODEL = PACKAGE_DIR / "bnpl_model_production.pkl"

RS = 42
TEST_SIZE_OUTER = 0.3
TEST_SIZE_INNER = 0.5


def _type_clean(df: pd.DataFrame) -> pd.DataFrame:
    out = df.copy()
    for col in out.columns:
        if out[col].dtype == "object" and col != "TARGET":
            try:
                out[col] = pd.to_numeric(out[col])
            except Exception:
                pass
    return out


def _feature_engineering(df: pd.DataFrame) -> pd.DataFrame:
    return feature_engineering_bnpl(df)


def _apply_clip_saved(df: pd.DataFrame, meta: dict) -> pd.DataFrame:
    out = df.copy()
    lower = meta.get("clip_lower") or {}
    upper = meta.get("clip_upper") or {}
    cols = meta.get("num_cols_clip") or []
    if not lower or not cols:
        print(
            "[WARN] Pas de bornes de clip dans le .pkl — clip 1%/99% sur ce jeu (pas identique a l'entrainement).",
            file=sys.stderr,
        )
        num_cols = out.select_dtypes(include=[np.number]).columns
        lo = out[num_cols].quantile(0.01)
        hi = out[num_cols].quantile(0.99)
        out[num_cols] = out[num_cols].clip(lower=lo, upper=hi, axis=1)
        return out
    for c in cols:
        if c not in out.columns:
            continue
        lo = lower.get(c)
        hi = upper.get(c)
        if lo is None or hi is None:
            continue
        out[c] = out[c].clip(lower=lo, upper=hi)
    return out


def _encode_categoricals(df: pd.DataFrame, meta: dict) -> pd.DataFrame:
    out = df.copy()
    encoders = meta.get("encoders") or {}
    cat_cols = meta.get("cat_cols")
    if not encoders:
        print("[WARN] Pas d'encodeurs dans le .pkl — impossible de reproduire l'encodage.", file=sys.stderr)
        return out
    cols = cat_cols if cat_cols is not None else list(encoders.keys())
    for col in cols:
        if col not in out.columns or col == "TARGET":
            continue
        if col not in encoders:
            continue
        le = encoders[col]
        val = out[col].astype(str)
        mapped = []
        for v in val:
            if v not in le.classes_:
                mapped.append(0)
            else:
                mapped.append(int(le.transform([v])[0]))
        out[col] = mapped
    return out


def _ensure_numeric_features(X: pd.DataFrame) -> pd.DataFrame:
    """LightGBM n'accepte que int/float/bool ; factorise les colonnes restees object/str."""
    out = X.copy()
    for c in out.columns:
        if pd.api.types.is_bool_dtype(out[c]):
            out[c] = out[c].astype(np.float64)
        elif not pd.api.types.is_numeric_dtype(out[c]):
            codes, _ = pd.factorize(out[c].astype(str), sort=False)
            out[c] = np.where(codes < 0, 0, codes).astype(np.float64)
    return out.astype(np.float64)


def preprocess_full(df_raw: pd.DataFrame, meta: dict) -> tuple[pd.DataFrame, pd.Series | None]:
    """Meme ordre que train_GBMlight : clean -> FE -> clip -> encode -> drop montant -> X."""
    required = {
        "revenu_mensuel_net",
        "revenu_annuel",
        "charges_mensuelles_totales",
        "montant_demande",
        "nbr_mois_remboursement",
        "anciennete_emploi_mois",
        "type_contrat",
    }
    miss = required - set(df_raw.columns)
    if miss:
        raise ValueError(f"Colonnes manquantes : {sorted(miss)}")

    df = _type_clean(df_raw)
    y = df["TARGET"].astype(int).copy() if "TARGET" in df.columns else None
    df = _feature_engineering(df)
    df = _apply_clip_saved(df, meta)
    df = _encode_categoricals(df, meta)
    if "montant_demande" in df.columns:
        df = df.drop(columns=["montant_demande"])

    features = meta.get("features") or []
    if not features:
        raise ValueError("Le .pkl ne contient pas la liste 'features'.")
    X = df.reindex(columns=features, fill_value=0.0)
    X = _ensure_numeric_features(X)
    return X, y


def main() -> int:
    parser = argparse.ArgumentParser(description="Test modele BNPL sur holdout (reste du dataset)")
    parser.add_argument("-i", "--input", type=Path, default=DEFAULT_CSV, help="CSV avec les memes colonnes que l'entrainement")
    parser.add_argument("-m", "--model", type=Path, default=DEFAULT_MODEL, help="Fichier .pkl (joblib) produit par train_GBMlight.py")
    parser.add_argument(
        "--holdout",
        choices=("test", "temp"),
        default="test",
        help="test = jeu test final uniquement (~15%%). temp = val+test (~30%%, hors train LGBM).",
    )
    args = parser.parse_args()

    if not args.input.is_file():
        print(f"CSV introuvable : {args.input.resolve()}", file=sys.stderr)
        return 1
    if not args.model.is_file():
        print(f"Modele introuvable : {args.model.resolve()}", file=sys.stderr)
        return 1

    meta = joblib.load(args.model)
    model = meta["model"]
    threshold = float(meta.get("threshold", 0.5))
    features = meta.get("features") or []

    df_raw = pd.read_csv(args.input)
    print(f"CSV : {args.input.resolve()} | lignes : {len(df_raw):,}")

    X_full, y_full = preprocess_full(df_raw, meta)

    if y_full is None:
        print("Pas de colonne TARGET : metriques impossibles (inference seulement).", file=sys.stderr)
        proba = model.predict_proba(X_full)[:, 1]
        print(f"Proba defaut (moyenne) : {proba.mean():.4f}")
        return 0

    X_train, X_temp, y_train, y_temp = train_test_split(
        X_full,
        y_full,
        test_size=TEST_SIZE_OUTER,
        random_state=RS,
        stratify=y_full,
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp,
        y_temp,
        test_size=TEST_SIZE_INNER,
        random_state=RS,
        stratify=y_temp,
    )

    if args.holdout == "test":
        X_eval, y_eval = X_test, y_test
        label = "HOLDOUT = jeu TEST final (hors train LGBM, ~15% du CSV)"
    else:
        X_eval = pd.concat([X_val, X_test], axis=0)
        y_eval = pd.concat([y_val, y_test], axis=0)
        label = "HOLDOUT = VAL + TEST (hors train LGBM, ~30% du CSV)"

    print("\n" + "=" * 60)
    print(label)
    print("=" * 60)
    print(f"Lignes evaluees : {len(X_eval):,}")
    print(f"Taux defaut (TARGET=1) : {y_eval.mean():.2%}")
    print(f"Seuil charge depuis le .pkl : {threshold:.6f}")

    proba = model.predict_proba(X_eval)[:, 1]
    pred = (proba >= threshold).astype(int)

    print("\nROC-AUC :", f"{roc_auc_score(y_eval, proba):.4f}")
    print("PR-AUC  :", f"{average_precision_score(y_eval, proba):.4f}")
    print("\nClassification (seuil du .pkl) :\n")
    print(classification_report(y_eval, pred, digits=4, zero_division=0))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
