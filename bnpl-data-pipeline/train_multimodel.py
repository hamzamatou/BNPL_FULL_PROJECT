"""
Multi-modèles : XGBoost (scoring), régression logistique (explicabilité),
Isolation Forest (anomalies / fraude).

Usage :
  pip install -r requirements.txt
  python train_multimodel.py
  python train_multimodel.py -i .\\out\\dataset_bnpl_tunisien_cleanV3.csv

Deux formats CSV acceptés :
  - bnpl_synthetic.csv : colonnes comme dans ton snippet (target minuscule, etc.)
  - sortie prepare_data.py : colonnes revenu_mensuel_net, TARGET, … (dérivés calculés auto)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import IsolationForest
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    classification_report,
    precision_recall_curve,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from xgboost import XGBClassifier

PACKAGE_DIR = Path(__file__).resolve().parent
DEFAULT_CSV = PACKAGE_DIR / "out" / "dataset_bnpl_tunisien_cleanV3.csv"
MODELS_DIR = PACKAGE_DIR / "out" / "models"

NUM_COLS_SCHEMA = [
    "anciennete_emploi",
    "revenu_mensuel",
    "credits_en_cours",
    "montant_demande",
    "nb_mois_remboursement",
    "mensualite",
    "debt_ratio",
    "effort_rate",
    "engagement_ratio",
    "reste_mensuel",
    "montant_sur_revenu",
    "revenu_annuel_k",
    "multiplicateur_panier",
    "mensualites_credits_existants",
    "taux_endettement_bct",
    "reste_apres_bnpl",
    "plafond_mensualite_bnpl",
    "ratio_montant_plafond",
    "log_revenu_net",
    "log_montant",
    "client_banque",
    "premier_achat_bnpl",
]
CAT_COLS = ["type_contrat", "canal", "segment_marchand"]


def make_preprocessor() -> ColumnTransformer:
    return ColumnTransformer(
        [
            ("num", StandardScaler(), NUM_COLS_SCHEMA),
            (
                "cat",
                OneHotEncoder(handle_unknown="ignore", sparse_output=False),
                CAT_COLS,
            ),
        ]
    )


def _build_from_pipeline_csv(df: pd.DataFrame) -> pd.DataFrame:
    """Transforme la sortie prepare_data.py vers le schéma attendu par les modèles."""
    if "TARGET" in df.columns:
        df = df.rename(columns={"TARGET": "target"})

    rm = df["revenu_mensuel_net"].astype(float)
    rm_safe = rm.replace(0, np.nan)
    mois = df["nbr_mois_remboursement"].astype(float).replace(0, np.nan)
    mensualite = (df["montant_demande"].astype(float) / mois).fillna(0.0)
    charges = df["charges_mensuelles_totales"].astype(float)
    montant = df["montant_demande"].astype(float)

    debt_ratio = (charges / rm_safe).replace([np.inf, -np.inf], np.nan).fillna(0.0)
    effort_rate = (mensualite / rm_safe).replace([np.inf, -np.inf], np.nan).fillna(0.0)
    engagement_ratio = (
        ((charges + mensualite) / rm_safe).replace([np.inf, -np.inf], np.nan).fillna(0.0)
    )
    reste_mensuel = rm - charges - mensualite
    montant_sur_revenu = (montant / rm_safe).replace([np.inf, -np.inf], np.nan).fillna(0.0)

    if "revenu_annuel" in df.columns:
        ra = df["revenu_annuel"].astype(float)
    else:
        ra = rm * 12.0
    revenu_annuel_k = ra / 1000.0

    rich = "taux_endettement_bct" in df.columns and "canal" in df.columns

    if rich:
        mc = df["mensualites_credits_existants"].astype(float)
        plaf = df["plafond_mensualite_bnpl"].astype(float)
        out = pd.DataFrame(
            {
                "anciennete_emploi": df["anciennete_emploi_mois"].astype(float),
                "revenu_mensuel": rm,
                "credits_en_cours": mc,
                "montant_demande": montant,
                "nb_mois_remboursement": df["nbr_mois_remboursement"].astype(float),
                "mensualite": mensualite,
                "debt_ratio": debt_ratio,
                "effort_rate": effort_rate,
                "engagement_ratio": engagement_ratio,
                "reste_mensuel": reste_mensuel,
                "montant_sur_revenu": montant_sur_revenu,
                "revenu_annuel_k": revenu_annuel_k,
                "multiplicateur_panier": df["multiplicateur_panier"].astype(float),
                "mensualites_credits_existants": mc,
                "taux_endettement_bct": df["taux_endettement_bct"].astype(float),
                "reste_apres_bnpl": df["reste_apres_bnpl"].astype(float),
                "plafond_mensualite_bnpl": plaf,
                "ratio_montant_plafond": df["ratio_montant_plafond"].astype(float),
                "log_revenu_net": df["log_revenu_net"].astype(float),
                "log_montant": df["log_montant"].astype(float),
                "client_banque": df["client_banque"].astype(float),
                "premier_achat_bnpl": df["premier_achat_bnpl"].astype(float),
                "type_contrat": df["type_contrat"].astype(str),
                "canal": df["canal"].astype(str),
                "segment_marchand": df["segment_marchand"].astype(str),
                "target": df["target"].astype(int),
            }
        )
        return out

    # Ancien CSV sans colonnes enrichies : proxies minimaux
    plafond_m = np.maximum(0.0, 0.40 * rm - 0.0)
    montant_cap = plafond_m * df["nbr_mois_remboursement"].astype(float)
    ratio_mp = (
        (montant / np.maximum(montant_cap, 1.0)).replace([np.inf, -np.inf], np.nan).fillna(0.0).clip(0, 100)
    )
    out = pd.DataFrame(
        {
            "anciennete_emploi": df["anciennete_emploi_mois"].astype(float),
            "revenu_mensuel": rm,
            "credits_en_cours": np.zeros(len(df)),
            "montant_demande": montant,
            "nb_mois_remboursement": df["nbr_mois_remboursement"].astype(float),
            "mensualite": mensualite,
            "debt_ratio": debt_ratio,
            "effort_rate": effort_rate,
            "engagement_ratio": engagement_ratio,
            "reste_mensuel": reste_mensuel,
            "montant_sur_revenu": montant_sur_revenu,
            "revenu_annuel_k": revenu_annuel_k,
            "multiplicateur_panier": np.ones(len(df)),
            "mensualites_credits_existants": np.zeros(len(df)),
            "taux_endettement_bct": effort_rate,
            "reste_apres_bnpl": reste_mensuel,
            "plafond_mensualite_bnpl": plafond_m,
            "ratio_montant_plafond": ratio_mp,
            "log_revenu_net": np.log1p(rm.clip(lower=0)),
            "log_montant": np.log1p(montant.clip(lower=0)),
            "client_banque": np.zeros(len(df)),
            "premier_achat_bnpl": np.zeros(len(df)),
            "type_contrat": df["type_contrat"].astype(str),
            "canal": np.repeat("web", len(df)),
            "segment_marchand": np.repeat("generaliste", len(df)),
            "target": df["target"].astype(int),
        }
    )
    return out


def _enrich_synthetic_features(df: pd.DataFrame) -> pd.DataFrame:
    """Complète les colonnes dérivées pour le format bnpl_synthetic (anciens CSV)."""
    df = df.copy()
    rm = df["revenu_mensuel"].astype(float)
    rm_safe = rm.replace(0, np.nan)
    n = len(df)

    if "engagement_ratio" not in df.columns:
        df["engagement_ratio"] = (
            df["debt_ratio"].astype(float) + df["effort_rate"].astype(float)
        )
    if "reste_mensuel" not in df.columns:
        df["reste_mensuel"] = rm - df["debt_ratio"].astype(float) * rm - df["effort_rate"].astype(float) * rm
    if "montant_sur_revenu" not in df.columns:
        df["montant_sur_revenu"] = (
            (df["montant_demande"].astype(float) / rm_safe)
            .replace([np.inf, -np.inf], np.nan)
            .fillna(0.0)
        )
    if "revenu_annuel_k" not in df.columns:
        df["revenu_annuel_k"] = (rm * 12.0) / 1000.0

    if "multiplicateur_panier" not in df.columns:
        df["multiplicateur_panier"] = np.ones(n)
    if "mensualites_credits_existants" not in df.columns:
        df["mensualites_credits_existants"] = np.zeros(n)
    if "taux_endettement_bct" not in df.columns:
        df["taux_endettement_bct"] = (
            (df["mensualites_credits_existants"].astype(float) + df["mensualite"].astype(float))
            / rm_safe.replace(0, np.nan)
        ).replace([np.inf, -np.inf], np.nan).fillna(0.0)
    if "reste_apres_bnpl" not in df.columns:
        df["reste_apres_bnpl"] = df["reste_mensuel"]
    if "plafond_mensualite_bnpl" not in df.columns:
        df["plafond_mensualite_bnpl"] = np.maximum(
            0.0, 0.40 * rm.values - df["mensualites_credits_existants"].astype(float).values
        )
    if "ratio_montant_plafond" not in df.columns:
        pm = df["plafond_mensualite_bnpl"].astype(float)
        mc = pm * df["nb_mois_remboursement"].astype(float)
        df["ratio_montant_plafond"] = (
            df["montant_demande"].astype(float) / np.maximum(mc, 1.0)
        ).replace([np.inf, -np.inf], np.nan).fillna(0.0)
    if "log_revenu_net" not in df.columns:
        df["log_revenu_net"] = np.log1p(rm.clip(lower=0))
    if "log_montant" not in df.columns:
        df["log_montant"] = np.log1p(df["montant_demande"].astype(float).clip(lower=0))
    if "client_banque" not in df.columns:
        df["client_banque"] = 0.0
    if "premier_achat_bnpl" not in df.columns:
        df["premier_achat_bnpl"] = 0.0
    if "canal" not in df.columns:
        df["canal"] = "web"
    if "segment_marchand" not in df.columns:
        df["segment_marchand"] = "generaliste"

    return df


def load_bnpl_frame(csv_path: Path) -> pd.DataFrame:
    df = pd.read_csv(csv_path)
    cols_lower = {c.lower(): c for c in df.columns}

    # Format bnpl_synthetic.csv (snake_case + target) ; colonnes dérivées ajoutées après coup
    present_syn = {c.lower() for c in df.columns}
    need_syn_base = set(
        [
            "anciennete_emploi",
            "revenu_mensuel",
            "credits_en_cours",
            "montant_demande",
            "nb_mois_remboursement",
            "mensualite",
            "debt_ratio",
            "effort_rate",
            "type_contrat",
            "target",
        ]
    )
    if need_syn_base.issubset(present_syn):
        rename = {}
        for want in need_syn_base:
            if want not in df.columns and want in cols_lower:
                rename[cols_lower[want]] = want
        out = df.rename(columns=rename)
        out = _enrich_synthetic_features(out)
        return out[list(NUM_COLS_SCHEMA + CAT_COLS + ["target"])].copy()

    # Format prepare_data (TARGET majuscule)
    pl_cols = {
        "revenu_mensuel_net",
        "charges_mensuelles_totales",
        "montant_demande",
        "nbr_mois_remboursement",
        "anciennete_emploi_mois",
        "type_contrat",
        "TARGET",
    }
    if pl_cols.issubset(df.columns):
        return _build_from_pipeline_csv(df)

    raise ValueError(
        "CSV non reconnu. Attendu soit :\n"
        f"  - bnpl_synthetic : {sorted(need_syn_base)}\n"
        f"  - prepare_data   : {sorted(pl_cols)}"
    )


def trouver_seuil_optimal(y_true, proba, recall_min: float = 0.55) -> tuple[float, float]:
    """F1-max sur la courbe PR + dernier seuil (grille) avec recall(defaut) >= recall_min."""
    precisions, recalls, thresholds = precision_recall_curve(y_true, proba)
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


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("-i", "--input", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--test-size", type=float, default=0.2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--quick",
        action="store_true",
        help="Grilles réduites + cv=2 (beaucoup plus rapide pour tester ; qualité un peu moins robuste).",
    )
    parser.add_argument("--verbose", type=int, default=1, help="GridSearchCV verbose (0=silencieux)")
    parser.add_argument(
        "--strong",
        action="store_true",
        help="Grille XGBoost plus large (n_estimators 300–400, max_depth jusqu'à 9) — plus long, souvent un peu mieux.",
    )
    args = parser.parse_args()

    csv_path = Path(args.input)
    if not csv_path.is_file():
        print(f"Fichier introuvable : {csv_path}", file=sys.stderr)
        return 1

    df = load_bnpl_frame(csv_path)
    X = df.drop(columns=["target"])
    y = df["target"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=args.test_size, random_state=args.seed, stratify=y if y.nunique() > 1 else None
    )

    # XGBoost : compenser le fort déséquilibre (sinon le modèle prédit presque tout en classe 0)
    n_pos = int((y_train == 1).sum())
    n_neg = int((y_train == 0).sum())
    scale_pos_weight = float(n_neg) / max(1, n_pos)
    print(
        f"[XGBoost] Desequilibre train : {n_neg:,} negatifs / {n_pos:,} positifs -> "
        f"scale_pos_weight={scale_pos_weight:.2f}\n"
    )

    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    # ---------- XGBoost ----------
    xgb_pipeline = Pipeline(
        [
            ("preprocessor", make_preprocessor()),
            (
                "model",
                XGBClassifier(
                    eval_metric="logloss",
                    random_state=args.seed,
                    n_jobs=-1,
                    scale_pos_weight=scale_pos_weight,
                    tree_method="hist",
                    min_child_weight=2,
                    reg_lambda=1.0,
                ),
            ),
        ]
    )

    if args.quick:
        xgb_param_grid = {
            "model__n_estimators": [100, 200],
            "model__max_depth": [5, 7],
            "model__learning_rate": [0.1],
            "model__subsample": [0.8],
            "model__colsample_bytree": [0.8, 1.0],
        }
        cv_folds = 2
    elif args.strong:
        xgb_param_grid = {
            "model__n_estimators": [200, 300, 400],
            "model__max_depth": [5, 7, 9],
            "model__learning_rate": [0.05, 0.1],
            "model__subsample": [0.8, 1.0],
            "model__colsample_bytree": [0.8, 1.0],
        }
        cv_folds = 3
    else:
        xgb_param_grid = {
            "model__n_estimators": [100, 200],
            "model__max_depth": [3, 5, 7],
            "model__learning_rate": [0.05, 0.1, 0.2],
            "model__subsample": [0.8, 1.0],
            "model__colsample_bytree": [0.8, 1.0],
        }
        cv_folds = 3

    n_xgb = 1
    for v in xgb_param_grid.values():
        n_xgb *= len(v)
    print(f"[XGBoost GridSearch] {n_xgb} combinaisons × {cv_folds} folds = {n_xgb * cv_folds} fits\n")

    # "f1" seul pousse souvent à tout prédire 0 quand ~90 % des lignes sont 0 ; PR-AUC mieux adapté.
    xgb_grid = GridSearchCV(
        xgb_pipeline,
        xgb_param_grid,
        cv=cv_folds,
        scoring="average_precision",
        n_jobs=-1,
        verbose=args.verbose,
    )
    xgb_grid.fit(X_train, y_train)
    best_xgb = xgb_grid.best_estimator_

    print("\nBest XGBoost Params:")
    print(xgb_grid.best_params_)

    y_pred_xgb = best_xgb.predict(X_test)
    proba_xgb = best_xgb.predict_proba(X_test)[:, 1]
    print("\nXGBoost — ROC-AUC (test, proba) :", f"{roc_auc_score(y_test, proba_xgb):.4f}")
    print(
        "XGBoost — PR-AUC / average_precision (test) :",
        f"{average_precision_score(y_test, proba_xgb):.4f}",
        "  (souvent plus parlant que ROC avec ~9 % de défauts)",
    )
    print("\nXGBoost Report (seuil sklearn par défaut = 0,5) :\n")
    print(classification_report(y_test, y_pred_xgb, digits=4, zero_division=0))

    seuil_f1_xgb, seuil_metier_xgb = trouver_seuil_optimal(y_test, proba_xgb, recall_min=0.55)
    print(f"\n  Seuils dérivés des probas (jeu test) :")
    print(f"    F1-max (classe défaut)     : {seuil_f1_xgb:.3f}")
    print(f"    Metier (recall defaut>=55%) : {seuil_metier_xgb:.3f}")
    print("\nXGBoost Report (seuil F1-max sur proba, pas 0,5) :\n")
    pred_f1 = (proba_xgb >= seuil_f1_xgb).astype(int)
    print(classification_report(y_test, pred_f1, digits=4, zero_division=0))

    joblib.dump(best_xgb, MODELS_DIR / "xgboost_model.pkl")
    meta_xgb = {
        "seuil_f1": seuil_f1_xgb,
        "seuil_metier": seuil_metier_xgb,
        "roc_auc_test": float(roc_auc_score(y_test, proba_xgb)),
        "pr_auc_test": float(average_precision_score(y_test, proba_xgb)),
    }
    joblib.dump(meta_xgb, MODELS_DIR / "xgboost_meta.joblib")

    # ---------- Logistic Regression ----------
    log_pipeline = Pipeline(
        [
            ("preprocessor", make_preprocessor()),
            ("model", LogisticRegression(max_iter=2000, class_weight="balanced", solver="lbfgs")),
        ]
    )

    # Ne pas passer penalty/solver dans la grille : sklearn ≥1.8 déprécie penalty dans GridSearch ;
    # solver=lbfgs + défaut = L2 (compatible avec ton usage précédent).
    log_param_grid = (
        {"model__C": [0.1, 1]}
        if args.quick
        else {"model__C": [0.01, 0.1, 1, 10]}
    )

    n_log = 1
    for v in log_param_grid.values():
        n_log *= len(v)
    print(f"[Logistic GridSearch] {n_log} combinaisons × {cv_folds} folds = {n_log * cv_folds} fits\n")

    log_grid = GridSearchCV(
        log_pipeline,
        log_param_grid,
        cv=cv_folds,
        scoring="f1",
        n_jobs=-1,
        verbose=args.verbose,
    )
    log_grid.fit(X_train, y_train)
    best_log = log_grid.best_estimator_

    print("\nBest Logistic Regression Params:")
    print(log_grid.best_params_)

    y_pred_log = best_log.predict(X_test)
    print("\nLogistic Regression Report:\n")
    print(classification_report(y_test, y_pred_log, digits=4, zero_division=0))

    joblib.dump(best_log, MODELS_DIR / "logistic_model.pkl")

    # ---------- Isolation Forest (numériques seuls) ----------
    fraud_features = df[NUM_COLS_SCHEMA]
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(fraud_features)

    iso_param_grid = {
        "n_estimators": [100, 200],
        "contamination": [0.03, 0.05, 0.1],
        "max_samples": ["auto", 0.8],
    }

    best_iso = None
    best_score = -1.0

    for n in iso_param_grid["n_estimators"]:
        for c in iso_param_grid["contamination"]:
            for m in iso_param_grid["max_samples"]:
                model = IsolationForest(
                    n_estimators=n,
                    contamination=c,
                    max_samples=m,
                    random_state=args.seed,
                    n_jobs=-1,
                )
                model.fit(X_scaled)
                preds = model.predict(X_scaled)
                anomaly_ratio = (preds == -1).mean()
                score = 1.0 - abs(anomaly_ratio - c)
                if score > best_score:
                    best_score = score
                    best_iso = model

    print("\nBest Isolation Forest selected (score proxy contamination).")

    joblib.dump(best_iso, MODELS_DIR / "isolation_forest.pkl")
    joblib.dump(scaler, MODELS_DIR / "fraud_scaler.pkl")

    print(f"\nModèles enregistrés dans : {MODELS_DIR.resolve()}")
    print(
        "  xgboost_model.pkl, xgboost_meta.joblib (seuils F1/métier), "
        "logistic_model.pkl, isolation_forest.pkl, fraud_scaler.pkl"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
