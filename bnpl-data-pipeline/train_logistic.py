"""
Entraînement régression logistique — scoring BNPL
Recherche automatique du seuil (F1-max + seuil métier recall_min)

Usage :
  python train_logistic.py
  python train_logistic.py -i .\\out\\dataset_bnpl_tunisien_cleanV3.csv
  python train_logistic.py --seuil 0.3
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

# Définies une seule fois : utilisées par le preprocessor ET colonnes_requises()
# (ColumnTransformer n'a pas transformers_ avant fit — ne pas lire depuis là.)
CATEGORICAL_FEATURES = ["type_contrat", "canal", "segment_marchand"]
NUMERIC_FEATURES = [
    "revenu_mensuel_net",
    "revenu_annuel",
    "charges_mensuelles_totales",
    "multiplicateur_panier",
    "mensualite_bnpl",
    "mensualites_credits_existants",
    "taux_endettement_bct",
    "reste_apres_bnpl",
    "plafond_mensualite_bnpl",
    "ratio_mensualite_revenu",
    "ratio_montant_plafond",
    "log_revenu_net",
    "log_montant",
    "montant_demande",
    "nbr_mois_remboursement",
    "anciennete_emploi_mois",
    "client_banque",
    "premier_achat_bnpl",
]


def build_pipeline() -> Pipeline:
    preprocessor = ColumnTransformer(
        transformers=[
            ("num", StandardScaler(), NUMERIC_FEATURES),
            (
                "cat",
                OneHotEncoder(
                    drop="first",
                    sparse_output=False,
                    handle_unknown="ignore",
                ),
                CATEGORICAL_FEATURES,
            ),
        ]
    )

    clf = LogisticRegression(
        class_weight="balanced",
        max_iter=2000,
        solver="lbfgs",
        random_state=42,
    )

    return Pipeline([("prep", preprocessor), ("clf", clf)])


def colonnes_requises() -> set[str]:
    return set(NUMERIC_FEATURES) | set(CATEGORICAL_FEATURES)


def enrichir_dataframe_bnpl(df: pd.DataFrame) -> pd.DataFrame:
    """
    Complète les colonnes enrichies si CSV ancien (sans prepare_data récent).
    Ne réécrit pas les colonnes déjà présentes.
    """
    out = df.copy()
    rm = out["revenu_mensuel_net"].astype(float)
    rm_s = rm.replace(0, np.nan)
    nm = out["nbr_mois_remboursement"].astype(float).replace(0, np.nan)
    md = out["montant_demande"].astype(float)
    ch = out["charges_mensuelles_totales"].astype(float)
    mens = (md / nm).fillna(0.0)

    if "revenu_annuel" not in out.columns:
        out["revenu_annuel"] = (rm * 12.0).round(0)

    if "mensualite_bnpl" not in out.columns:
        out["mensualite_bnpl"] = mens

    if "mensualites_credits_existants" not in out.columns:
        out["mensualites_credits_existants"] = np.minimum(rm.values * 0.10, 250.0)

    mc = out["mensualites_credits_existants"].astype(float)

    if "multiplicateur_panier" not in out.columns:
        out["multiplicateur_panier"] = 1.12

    if "plafond_mensualite_bnpl" not in out.columns:
        out["plafond_mensualite_bnpl"] = np.maximum(0.0, 0.40 * rm.values - mc.values)

    plaf = out["plafond_mensualite_bnpl"].astype(float)
    if "ratio_montant_plafond" not in out.columns:
        cap = np.maximum(plaf.values * out["nbr_mois_remboursement"].astype(float).values, 1.0)
        out["ratio_montant_plafond"] = np.minimum((md.values / cap).astype(float), 100.0)

    if "taux_endettement_bct" not in out.columns:
        out["taux_endettement_bct"] = (
            ((mc + mens.astype(float)) / rm_s).replace([np.inf, -np.inf], np.nan).fillna(0.0).clip(0, 3)
        )

    if "reste_apres_bnpl" not in out.columns:
        out["reste_apres_bnpl"] = rm - ch - mens.astype(float)

    if "ratio_mensualite_revenu" not in out.columns:
        out["ratio_mensualite_revenu"] = (
            (mens.astype(float) / rm_s).replace([np.inf, -np.inf], np.nan).fillna(0.0).clip(0, 5)
        )

    if "log_revenu_net" not in out.columns:
        out["log_revenu_net"] = np.log1p(rm.clip(lower=0))

    if "log_montant" not in out.columns:
        out["log_montant"] = np.log1p(md.clip(lower=0))

    if "canal" not in out.columns:
        out["canal"] = "web"

    if "segment_marchand" not in out.columns:
        out["segment_marchand"] = "generaliste"

    if "client_banque" not in out.columns:
        out["client_banque"] = 0

    if "premier_achat_bnpl" not in out.columns:
        out["premier_achat_bnpl"] = 0

    return out


def completer_ligne_inference(row: dict) -> dict:
    """Complète les features enrichies pour démo / inférence si absentes."""
    rm = float(row["revenu_mensuel_net"])
    md = float(row["montant_demande"])
    nm = float(row["nbr_mois_remboursement"])
    ch = float(row["charges_mensuelles_totales"])
    mens = md / max(nm, 1.0)
    mc = float(row.get("mensualites_credits_existants", min(220.0, 0.10 * rm)))
    plaf = float(row.get("plafond_mensualite_bnpl", max(0.0, 0.40 * rm - mc)))
    cap = max(plaf * nm, 1.0)
    base = {
        "revenu_annuel": float(row.get("revenu_annuel", rm * 12)),
        "multiplicateur_panier": float(row.get("multiplicateur_panier", 1.12)),
        "mensualite_bnpl": float(row.get("mensualite_bnpl", mens)),
        "mensualites_credits_existants": mc,
        "taux_endettement_bct": float(row.get("taux_endettement_bct", (mc + mens) / max(rm, 1e-9))),
        "reste_apres_bnpl": float(row.get("reste_apres_bnpl", rm - ch - mens)),
        "plafond_mensualite_bnpl": plaf,
        "ratio_mensualite_revenu": float(row.get("ratio_mensualite_revenu", mens / max(rm, 1e-9))),
        "ratio_montant_plafond": float(row.get("ratio_montant_plafond", md / cap)),
        "log_revenu_net": float(row.get("log_revenu_net", np.log1p(max(rm, 0)))),
        "log_montant": float(row.get("log_montant", np.log1p(max(md, 0)))),
        "client_banque": int(row.get("client_banque", 1)),
        "premier_achat_bnpl": int(row.get("premier_achat_bnpl", 0)),
        "canal": str(row.get("canal", "web")),
        "segment_marchand": str(row.get("segment_marchand", "generaliste")),
        "type_contrat": str(row["type_contrat"]),
    }
    out = {**row, **base}
    return out


def trouver_seuil_optimal(y_true, proba, recall_min: float = 0.55) -> tuple[float, float]:
    """
    Retourne (seuil_f1, seuil_metier).

    - seuil_f1 : maximise F1 sur la classe défaut (indices alignés sklearn PR curve).
    - seuil_metier : plus grand seuil (sur une grille) tel que recall(defaut) >= recall_min.
      Recall diminue quand le seuil augmente → on parcourt t de 0 à 1 et on garde
      le dernier t qui satisfait encore recall >= recall_min (= seuil le plus strict possible).
    """
    precisions, recalls, thresholds = precision_recall_curve(y_true, proba)
    # len(thresholds) == len(precisions) - 1 — F1 sur les points associés à thresholds
    p_cut = precisions[:-1]
    r_cut = recalls[:-1]
    f1_each = np.where(
        p_cut + r_cut > 1e-12,
        2 * p_cut * r_cut / (p_cut + r_cut + 1e-12),
        0.0,
    )
    idx_f1 = int(np.argmax(f1_each))
    seuil_f1 = float(thresholds[idx_f1])

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
    print(
        classification_report(
            y_true,
            pred,
            target_names=["Remboursera (0)", "Défaut (1)"],
            digits=4,
            zero_division=0,
        )
    )


def decision_3_zones(proba_defaut: float) -> tuple[str, str]:
    """Inférence production : 3 zones sur la proba de défaut uniquement."""
    if proba_defaut < 0.25:
        return "APPROUVÉ", "Dossier solide — transmission banque possible"
    if proba_defaut < 0.45:
        return "ANALYSE", "Vérification analyste / banque recommandée"
    return "REFUSÉ", "Risque élevé — pré-scoring défavorable"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Régression logistique BNPL avec seuil optimal"
    )
    parser.add_argument("-i", "--input", type=Path, default=DEFAULT_CSV)
    parser.add_argument("-o", "--model-out", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--recall-min",
        type=float,
        default=0.55,
        help="Recall minimal souhaité sur les défauts pour le seuil métier",
    )
    parser.add_argument(
        "--seuil",
        type=float,
        default=None,
        help="Seuil fixe sauvegardé (ex: 0.3). Si absent → seuil F1-max.",
    )
    args = parser.parse_args()

    csv_path = Path(args.input)
    if not csv_path.is_file():
        print(f"Fichier introuvable : {csv_path.resolve()}", file=sys.stderr)
        return 1

    df = pd.read_csv(csv_path)
    print(f"Dataset : {len(df):,} lignes | Taux défaut : {df['TARGET'].mean():.2%}")

    base_needed = {
        "TARGET",
        "revenu_mensuel_net",
        "charges_mensuelles_totales",
        "montant_demande",
        "nbr_mois_remboursement",
        "anciennete_emploi_mois",
        "type_contrat",
    }
    miss_base = base_needed - set(df.columns)
    if miss_base:
        print(f"Colonnes minimales manquantes : {sorted(miss_base)}", file=sys.stderr)
        return 1

    df = enrichir_dataframe_bnpl(df)

    required = colonnes_requises() | {"TARGET"}
    missing = required - set(df.columns)
    if missing:
        print(f"Colonnes manquantes après enrichissement : {sorted(missing)}", file=sys.stderr)
        return 1

    y = df["TARGET"].astype(int)
    X = df.drop(columns=["TARGET"])

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=args.test_size,
        random_state=args.seed,
        stratify=y,
    )
    print(f"Train : {len(X_train):,} | Test : {len(X_test):,}")

    model = build_pipeline()
    print("\nEntraînement...")
    model.fit(X_train, y_train)

    proba = model.predict_proba(X_test)[:, 1]

    print("\n" + "=" * 55)
    print("  COMPARAISON DES SEUILS (jeu test)")
    print("=" * 55)

    afficher_resultats("SEUIL PAR DÉFAUT sklearn (0,50)", y_test, proba, 0.5)

    seuil_f1, seuil_metier = trouver_seuil_optimal(
        y_test, proba, recall_min=args.recall_min
    )
    print(f"\n  Seuil F1-max              : {seuil_f1:.3f}")
    print(f"  Seuil métier (recall>={args.recall_min:.0%}) : {seuil_metier:.3f}")

    afficher_resultats("SEUIL F1-MAX", y_test, proba, seuil_f1)
    afficher_resultats(
        f"SEUIL MÉTIER (recall défaut >= {args.recall_min:.0%})",
        y_test,
        proba,
        seuil_metier,
    )

    seuil_final = args.seuil if args.seuil is not None else seuil_f1
    origine = " (--seuil)" if args.seuil is not None else " (F1-max auto)"
    print(f"\n{'=' * 55}")
    print(f"  SEUIL FINAL RETENU (sauvegarde){origine} : {seuil_final:.3f}")
    print(f"{'=' * 55}")

    afficher_resultats(
        "SEUIL FINAL — métriques jeu test",
        y_test,
        proba,
        seuil_final,
    )

    # 3 zones : uniquement sur proba (indépendant du seuil binaire ci-dessus)
    total = len(proba)
    z_ok = int((proba < 0.25).sum())
    z_mid = int(((proba >= 0.25) & (proba < 0.45)).sum())
    z_ko = int((proba >= 0.45).sum())
    print("\n  Répartition indicielle (3 zones sur proba) :")
    print(f"  APPROUVÉ   P < 0,25          : {z_ok:>6,}  ({z_ok/total:.1%})")
    print(f"  ANALYSE    0,25 <= P < 0,45 : {z_mid:>6,}  ({z_mid/total:.1%})")
    print(f"  REFUSE     P >= 0,45        : {z_ko:>6,}  ({z_ko/total:.1%})")

    out_path = Path(args.model_out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    payload = {
        "pipeline": model,
        "seuil_f1": seuil_f1,
        "seuil_metier": seuil_metier,
        "seuil_final": seuil_final,
        "auc_test": roc_auc_score(y_test, proba),
        "zones": {"faible": 0.25, "moyen": 0.45},
    }
    joblib.dump(payload, out_path)
    print(f"\nModèle + seuils sauvegardés : {out_path.resolve()}")

    print("\n" + "=" * 55)
    print("  EXEMPLES D'INFÉRENCE — 3 PROFILS")
    print("=" * 55)

    demos = [
        {
            "profil": "CDI stable — 2 200 DT",
            "revenu_mensuel_net": 2200.0,
            "revenu_annuel": 28600.0,
            "charges_mensuelles_totales": 750.0,
            "montant_demande": 3000.0,
            "nbr_mois_remboursement": 12.0,
            "anciennete_emploi_mois": 48.0,
            "type_contrat": "CDI",
        },
        {
            "profil": "CDD charges élevées — 900 DT",
            "revenu_mensuel_net": 900.0,
            "revenu_annuel": 11700.0,
            "charges_mensuelles_totales": 620.0,
            "montant_demande": 2500.0,
            "nbr_mois_remboursement": 6.0,
            "anciennete_emploi_mois": 8.0,
            "type_contrat": "CDD",
        },
        {
            "profil": "CDI × 14 mois — 1 500 DT",
            "revenu_mensuel_net": 1500.0,
            "revenu_annuel": 21000.0,
            "charges_mensuelles_totales": 550.0,
            "montant_demande": 2000.0,
            "nbr_mois_remboursement": 12.0,
            "anciennete_emploi_mois": 36.0,
            "type_contrat": "CDI",
        },
    ]

    for row in demos:
        profil = row.pop("profil")
        row = completer_ligne_inference(row)
        cols = sorted(colonnes_requises())
        X_demo = pd.DataFrame([{c: row[c] for c in cols}])
        p = float(model.predict_proba(X_demo)[0, 1])
        mens_bnpl = row["montant_demande"] / max(row["nbr_mois_remboursement"], 1.0)
        taux = (row["charges_mensuelles_totales"] + mens_bnpl) / max(
            row["revenu_mensuel_net"], 1e-9
        )
        rav = row["revenu_mensuel_net"] - row["charges_mensuelles_totales"] - mens_bnpl
        dec, msg = decision_3_zones(p)

        print(f"\n  {profil}")
        print(f"    P(défaut)              : {p:.2%}")
        print(f"    Ratio charges+mens./rev : {taux:.1%}  (indicatif vs règle BCT)")
        print(f"    Reste après charges+BNPL : {rav:.0f} DT")
        print(f"    Zone                    : {dec}")
        print(f"    Détail                  : {msg}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
