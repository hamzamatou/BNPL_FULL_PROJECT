"""
Entraînement régression logistique — scoring BNPL
Recherche automatique du seuil (F1-max + seuil métier recall_min)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    classification_report,
    precision_recall_curve,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

PACKAGE_DIR = Path(__file__).resolve().parent
DEFAULT_CSV = PACKAGE_DIR / "out" / "dataset_bnpl_tunisien_cleanV3.csv"
DEFAULT_MODEL = PACKAGE_DIR / "out" / "models" / "logistic_bnpl.joblib"

CAT_COLS = ["type_contrat"]
NUM_COLS = [
    "revenu_mensuel_net",
    "revenu_annuel",
    "charges_mensuelles_totales",
    "montant_demande",
    "nbr_mois_remboursement",
    "anciennete_emploi_mois",
    # Feature engineering style LightGBM
    "mensualite_bnpl",
    "reste_a_vivre",
    "taux_effort",
    "ratio_bnpl_reste",
    "taux_endettement_global",
    "buffer_financier",
    "capacite_nette",
    "ratio_mensualite_revenu",
    "charge_totale_ratio",
    "stress_financier",
    "log_revenu",
    "log_montant",
]


def build_pipeline(class_weight: str | dict[int, float] | None = "balanced") -> Pipeline:
    preprocessor = ColumnTransformer(
        transformers=[
            ("num", StandardScaler(), NUM_COLS),
            (
                "cat",
                OneHotEncoder(drop="first", sparse_output=False, handle_unknown="ignore"),
                CAT_COLS,
            ),
        ]
    )
    clf = LogisticRegression(
        class_weight=class_weight,
        max_iter=2000,
        solver="lbfgs",
        random_state=42,
    )
    return Pipeline([("prep", preprocessor), ("clf", clf)])


def colonnes_requises() -> set[str]:
    return set(NUM_COLS) | set(CAT_COLS)


def feature_engineering_like_lightgbm(df: pd.DataFrame) -> pd.DataFrame:
    """
    Ajoute automatiquement des variables dérivées "style LightGBM"
    à partir du dataset minimal fourni par l'utilisateur.
    """
    out = df.copy()

    rm = out["revenu_mensuel_net"].astype(float)
    ch = out["charges_mensuelles_totales"].astype(float)
    md = out["montant_demande"].astype(float)
    duree = out["nbr_mois_remboursement"].astype(float)
    anc = out["anciennete_emploi_mois"].astype(float)

    denom_duree = np.maximum(duree, 1.0)
    denom_rev = rm + 1.0

    out["mensualite_bnpl"] = md / denom_duree
    out["reste_a_vivre"] = rm - ch
    out["taux_effort"] = ch / denom_rev
    out["ratio_bnpl_reste"] = out["mensualite_bnpl"] / (out["reste_a_vivre"] + 1.0)
    out["taux_endettement_global"] = (ch + out["mensualite_bnpl"]) / denom_rev
    out["buffer_financier"] = out["reste_a_vivre"] - out["mensualite_bnpl"]
    out["capacite_nette"] = rm - ch - out["mensualite_bnpl"]
    out["ratio_mensualite_revenu"] = out["mensualite_bnpl"] / denom_rev
    out["charge_totale_ratio"] = ch / denom_rev
    out["stress_financier"] = out["taux_endettement_global"] * anc
    out["log_revenu"] = np.log1p(np.clip(rm, a_min=0.0, a_max=None))
    out["log_montant"] = np.log1p(np.clip(md, a_min=0.0, a_max=None))

    return out


def trouver_seuil_optimal(y_true, proba, recall_min: float = 0.55) -> tuple[float, float]:
    precisions, recalls, thresholds = precision_recall_curve(y_true, proba)
    p_cut = precisions[:-1]
    r_cut = recalls[:-1]
    f1 = np.where(p_cut + r_cut > 1e-12, 2 * p_cut * r_cut / (p_cut + r_cut + 1e-12), 0.0)
    seuil_f1 = float(thresholds[int(np.argmax(f1))])

    seuil_metier = seuil_f1
    for t in np.linspace(0.0, 1.0, 1001):
        pred = (proba >= t).astype(int)
        rec = recall_score(y_true, pred, pos_label=1, zero_division=0)
        if rec >= recall_min:
            seuil_metier = float(t)
    return seuil_f1, seuil_metier


def afficher_resultats(label: str, y_true, proba, seuil: float) -> None:
    pred = (proba >= seuil).astype(int)
    auc = roc_auc_score(y_true, proba)
    acceptes = int((pred == 0).sum())
    refuses = int((pred == 1).sum())
    total = len(pred)
    print(f"\n{'-' * 55}")
    print(f"  {label}  (seuil = {seuil:.3f})")
    print(f"{'-' * 55}")
    print(f"  AUC-ROC    : {auc:.4f}")
    print(f"  Acceptés   : {acceptes:>6,}  ({acceptes/total:.1%})")
    print(f"  Refusés    : {refuses:>6,}  ({refuses/total:.1%})")
    print()
    print(classification_report(y_true, pred, target_names=["Remboursera (0)", "Défaut (1)"], digits=4, zero_division=0))


def decision_3_zones(proba_defaut: float) -> tuple[str, str]:
    if proba_defaut < 0.25:
        return "APPROUVE", "Dossier solide"
    if proba_defaut < 0.45:
        return "ANALYSE", "Verification analyste recommandee"
    return "REFUSE", "Risque eleve"


def main() -> int:
    parser = argparse.ArgumentParser(description="Régression logistique BNPL avec seuil optimal")
    parser.add_argument("-i", "--input", type=Path, default=DEFAULT_CSV)
    parser.add_argument("-o", "--model-out", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--recall-min", type=float, default=0.55)
    parser.add_argument("--seuil", type=float, default=None)
    parser.add_argument("--class-weight", choices=("balanced", "none"), default="balanced")
    parser.add_argument("--weight-defaut", type=float, default=None)
    args = parser.parse_args()

    if not Path(args.input).is_file():
        print(f"Fichier introuvable : {Path(args.input).resolve()}", file=sys.stderr)
        return 1

    df = pd.read_csv(args.input)
    print(f"Dataset : {len(df):,} lignes | Taux défaut : {df['TARGET'].mean():.2%}")
    required_minimal = {
        "revenu_mensuel_net",
        "revenu_annuel",
        "charges_mensuelles_totales",
        "montant_demande",
        "nbr_mois_remboursement",
        "anciennete_emploi_mois",
        "type_contrat",
        "TARGET",
    }
    missing_minimal = required_minimal - set(df.columns)
    if missing_minimal:
        print(f"Colonnes minimales manquantes : {sorted(missing_minimal)}", file=sys.stderr)
        return 1

    df = feature_engineering_like_lightgbm(df)

    y = df["TARGET"].astype(int)
    X = df.drop(columns=["TARGET"])
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.seed, stratify=y
    )
    print(f"Train : {len(X_train):,} | Test : {len(X_test):,}")

    if args.weight_defaut is not None:
        if args.weight_defaut <= 0:
            print("--weight-defaut doit être > 0", file=sys.stderr)
            return 1
        cw = {0: 1.0, 1: float(args.weight_defaut)}
        print(f"\nclass_weight explicite : {{0: 1, 1: {args.weight_defaut}}}")
    elif args.class_weight == "none":
        cw = None
        print("\nclass_weight=None (pas balanced) — compenser avec seuil F1 / métier / --seuil.")
    else:
        cw = "balanced"

    model = build_pipeline(class_weight=cw)
    print("\nEntraînement...")
    model.fit(X_train, y_train)
    proba = model.predict_proba(X_test)[:, 1]

    print("\n" + "=" * 55)
    print("  COMPARAISON DES SEUILS (jeu test)")
    print("=" * 55)
    afficher_resultats("SEUIL PAR DÉFAUT sklearn (0,50)", y_test, proba, 0.5)

    seuil_f1, seuil_metier = trouver_seuil_optimal(y_test, proba, recall_min=args.recall_min)
    print(f"\n  Seuil F1-max              : {seuil_f1:.3f}")
    print(f"  Seuil métier (recall>={args.recall_min:.0%}) : {seuil_metier:.3f}")
    afficher_resultats("SEUIL F1-MAX", y_test, proba, seuil_f1)
    afficher_resultats(f"SEUIL MÉTIER (recall défaut >= {args.recall_min:.0%})", y_test, proba, seuil_metier)

    seuil_final = args.seuil if args.seuil is not None else seuil_f1
    print(f"\n{'=' * 55}")
    print(f"  SEUIL FINAL RETENU (sauvegarde) : {seuil_final:.3f}")
    print(f"{'=' * 55}")
    afficher_resultats("SEUIL FINAL — métriques jeu test", y_test, proba, seuil_final)

    print("\n" + "=" * 55)
    print("  EXEMPLES DE TEST (INFERENCE)")
    print("=" * 55)
    demos = [
        {
            "profil": "CDI stable - 2200 DT",
            "revenu_mensuel_net": 2200.0,
            "revenu_annuel": 26400.0,
            "charges_mensuelles_totales": 750.0,
            "montant_demande": 3000.0,
            "nbr_mois_remboursement": 12.0,
            "anciennete_emploi_mois": 48.0,
            "type_contrat": "CDI",
        },
        {
            "profil": "CDD charge elevee - 900 DT",
            "revenu_mensuel_net": 900.0,
            "revenu_annuel": 10800.0,
            "charges_mensuelles_totales": 620.0,
            "montant_demande": 2500.0,
            "nbr_mois_remboursement": 6.0,
            "anciennete_emploi_mois": 8.0,
            "type_contrat": "CDD",
        },
        {
            "profil": "CDI moyen - 1500 DT",
            "revenu_mensuel_net": 1500.0,
            "revenu_annuel": 18000.0,
            "charges_mensuelles_totales": 550.0,
            "montant_demande": 2000.0,
            "nbr_mois_remboursement": 12.0,
            "anciennete_emploi_mois": 36.0,
            "type_contrat": "CDI",
        },
    ]
    for d in demos:
        profil = d.pop("profil")
        x_demo = feature_engineering_like_lightgbm(pd.DataFrame([d]))
        p_defaut = float(model.predict_proba(x_demo)[0, 1])
        dec, msg = decision_3_zones(p_defaut)
        print(f"\n  {profil}")
        print(f"    P(defaut)  : {p_defaut:.2%}")
        print(f"    Decision   : {dec}")
        print(f"    Detail     : {msg}")

    out = Path(args.model_out)
    out.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "pipeline": model,
            "seuil_f1": seuil_f1,
            "seuil_metier": seuil_metier,
            "seuil_final": seuil_final,
            "auc_test": roc_auc_score(y_test, proba),
            "class_weight_setting": cw,
        },
        out,
    )
    print(f"\nModèle + seuils sauvegardés : {out.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())