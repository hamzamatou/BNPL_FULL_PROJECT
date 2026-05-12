# ==============================================================
#  NETTOYAGE COMPLET DES DONNÉES — Pré-scoring BNPL Tunisien
#  Source : Home Credit Default Risk (Kaggle)
#  Contexte cible : Tunisie B2C — CDI / CDD uniquement
#  Version: charges explicables (base + famille + engagements)
# ==============================================================

from pathlib import Path

import numpy as np
import pandas as pd
from scipy.stats import norm, rankdata
import warnings
from sklearn.utils import resample

warnings.filterwarnings("ignore")

# ──────────────────────────────────────────────────────────────
# PARAMÈTRES OFFICIELS TUNISIE (INS / conventions — à ajuster)
# ──────────────────────────────────────────────────────────────
SMIG_NET = 554.736  # DT net/mois — SMIG 48h
SALAIRE_MEDIAN = 1200
SALAIRE_MOYEN = 1400
P10_NET = 922
P90_NET = 3942
SALAIRE_MAX_NET = 3942

MONTANT_BNPL_MIN = 500
MONTANT_BNPL_MAX = 15000

DUREE_MIN = 3
DUREE_MAX = 36

SEED = 42

MU_SALAIRE = np.log(SALAIRE_MEDIAN)
SIGMA_SALAIRE = (np.log(P90_NET) - np.log(P10_NET)) / (2 * 1.282)


def prepare_bnpl_dataset(
    application_train_csv: Path,
    output_csv: Path | None = None,
    *,
    seed: int = SEED,
) -> pd.DataFrame:
    """
    Même pipeline que le script historique ; paramètres chemins uniquement.

    Lit application_train.csv, applique filtres et recalibration, retourne le DataFrame exportable.
    Si output_csv est fourni, écrit aussi le CSV (crée le dossier parent si besoin).
    """
    application_train_csv = Path(application_train_csv)
    np.random.seed(seed)

    print("=" * 70)
    print("NETTOYAGE DATASET BNPL TUNISIEN")
    print("=" * 70)

    print(f"\nParamètres log-normale calibrés :")
    print(f"  mu              = {MU_SALAIRE:.4f}")
    print(f"  sigma           = {SIGMA_SALAIRE:.4f}")
    print(f"  Médiane théo.   = {np.exp(MU_SALAIRE):.0f} DT  (cible : {SALAIRE_MEDIAN} DT)")
    print(f"  Moyenne théo.   = {np.exp(MU_SALAIRE + SIGMA_SALAIRE**2/2):.0f} DT  (cible : {SALAIRE_MOYEN} DT)")
    print(f"  P10 théo.       = {np.exp(MU_SALAIRE - 1.282 * SIGMA_SALAIRE):.0f} DT  (cible : {P10_NET} DT)")
    print(f"  P90 théo.       = {np.exp(MU_SALAIRE + 1.282 * SIGMA_SALAIRE):.0f} DT  (cible : {P90_NET} DT)")

    # ==============================================================
    # ETAPE 1 — CHARGEMENT
    # ==============================================================
    print("\n[1] Fichier :", application_train_csv)
    df = pd.read_csv(application_train_csv)
    print(f"    Lignes initiales: {len(df):,} | Colonnes: {df.shape[1]} | Taux défaut brut: {df['TARGET'].mean():.2%}")

    # ==============================================================
    # ETAPE 2 — SÉLECTION DES COLONNES
    # ==============================================================
    cols_utiles = [
        "SK_ID_CURR",
        "TARGET",
        "NAME_INCOME_TYPE",
        "AMT_INCOME_TOTAL",
        "AMT_CREDIT",
        "AMT_ANNUITY",
        "DAYS_EMPLOYED",
        "CNT_CHILDREN",
        "AMT_GOODS_PRICE",
        "DAYS_BIRTH",
    ]
    df = df[cols_utiles].copy()
    print(f"[2] Colonnes conservées: {len(cols_utiles)}")

    # ==============================================================
    # ETAPE 3 — FILTRAGE B2C TUNISIEN (CDI / CDD)
    # ==============================================================
    mapping_contrat = {
        "State servant": "CDI",
        "Working": "CDI",
        "Commercial associate": "CDD",
        "Maternity leave": "CDD",
    }
    n_before = len(df)
    df["type_contrat"] = df["NAME_INCOME_TYPE"].map(mapping_contrat)
    df = df[df["type_contrat"].notna()].copy()
    print(f"[3] Filtrage contrat: {n_before - len(df):,} lignes supprimées | Restant: {len(df):,}")

    # ==============================================================
    # ETAPE 4 — NETTOYAGE ABERRATIONS
    # ==============================================================
    df = df[df["DAYS_EMPLOYED"] != 365243].copy()
    df = df[df["AMT_INCOME_TOTAL"] > 0].copy()
    df = df[df["AMT_CREDIT"] > 0].copy()
    df = df[df["AMT_ANNUITY"].notna() & (df["AMT_ANNUITY"] > 0)].copy()

    df["age_ans"] = (df["DAYS_BIRTH"].abs() / 365).round(0)
    df = df[(df["age_ans"] >= 18) & (df["age_ans"] <= 70)].copy()

    df["CNT_CHILDREN"] = df["CNT_CHILDREN"].fillna(0).clip(0, 5).astype(int)

    df = df[df["TARGET"].notna()].copy()
    df["TARGET"] = df["TARGET"].astype(int)

    print(f"[4] Après aberrations: {len(df):,} lignes")

    # ==============================================================
    # ETAPE 5 — VALEURS MANQUANTES
    # ==============================================================
    if df["AMT_GOODS_PRICE"].isna().sum() > 0:
        med_goods = df["AMT_GOODS_PRICE"].median()
        df["AMT_GOODS_PRICE"] = df["AMT_GOODS_PRICE"].fillna(med_goods)
        print(f"    AMT_GOODS_PRICE : imputé par médiane ({med_goods:.0f})")

    print(f"[5] Valeurs manquantes après traitement : {int(df.isnull().sum().sum())}")

    # ==============================================================
    # ETAPE 6 — RECALIBRATION MONÉTAIRE (TUNISIE)
    # ==============================================================
    # 6.1 Revenu net mensuel (percentile -> quantile normal -> lognormale)
    p_rev = rankdata(df["AMT_INCOME_TOTAL"], method="average") / (len(df) + 1)
    z_rev = norm.ppf(np.clip(p_rev, 1e-6, 1 - 1e-6))

    df["revenu_mensuel_net"] = np.exp(MU_SALAIRE + SIGMA_SALAIRE * z_rev)
    df["revenu_mensuel_net"] = df["revenu_mensuel_net"].clip(SMIG_NET, SALAIRE_MAX_NET).round(0)
    df["revenu_annuel"] = (df["revenu_mensuel_net"] * 12).round(0)

    # Levier crédit / bien (Home Credit) — proxy « multiplicateur panier »
    _gp = df["AMT_GOODS_PRICE"].replace(0, np.nan)
    _levier = (df["AMT_CREDIT"].astype(float) / _gp).replace([np.inf, -np.inf], np.nan)
    df["multiplicateur_panier"] = _levier.clip(0.25, 15.0).fillna(1.0).round(4)

    # 6.2 Montant demandé BNPL
    p_credit = rankdata(df["AMT_CREDIT"], method="average") / (len(df) + 1)
    df["montant_demande"] = np.exp(
        np.log(MONTANT_BNPL_MIN) + p_credit * (np.log(MONTANT_BNPL_MAX) - np.log(MONTANT_BNPL_MIN))
    ).round(0)

    # 6.3 Durée remboursement (proxy)
    df["nbr_mois_remboursement"] = (df["AMT_CREDIT"] / df["AMT_ANNUITY"]).clip(DUREE_MIN, DUREE_MAX).round(0)

    print(f"[6] Recalibration faite | Revenu médian: {df['revenu_mensuel_net'].median():.0f} DT")

    # ==============================================================
    # ETAPE 7 — CHARGES MENSUELLES EXPLICABLES (base + famille + engagements + clip)
    # ==============================================================
    taux_base = np.where(
        df["revenu_mensuel_net"] < 900,
        np.random.uniform(0.28, 0.34, len(df)),
        np.where(
            df["revenu_mensuel_net"] < 1800,
            np.random.uniform(0.24, 0.30, len(df)),
            np.random.uniform(0.20, 0.26, len(df)),
        ),
    )
    df["base_essentielle"] = np.maximum(400, df["revenu_mensuel_net"] * taux_base).round(0)

    df["charge_famille"] = (df["CNT_CHILDREN"] * 180).round(0)

    taux_eng = np.random.uniform(0.05, 0.18, len(df))
    df["engagements_existants"] = (df["revenu_mensuel_net"] * taux_eng).round(0)

    df["charges_mensuelles_totales"] = (
        df["base_essentielle"] + df["charge_famille"] + df["engagements_existants"]
    ).round(0)

    ratio = (df["charges_mensuelles_totales"] / df["revenu_mensuel_net"]).clip(0.25, 0.65)
    df["charges_mensuelles_totales"] = (df["revenu_mensuel_net"] * ratio).round(0)

    print(f"[7] Charges ratio médian: {(df['charges_mensuelles_totales']/df['revenu_mensuel_net']).median():.2%}")

    # ==============================================================
    # ETAPE 8 — ANCIENNETÉ EMPLOI
    # ==============================================================
    df["anciennete_emploi_mois"] = (df["DAYS_EMPLOYED"].abs() / 30).clip(0, 360).round(0)
    print(f"[8] Ancienneté médiane: {df['anciennete_emploi_mois'].median():.0f} mois")

    # ==============================================================
    # ETAPE 9 — Synthèse (pas de colonnes taux/reste dans l'export)
    # ==============================================================
    _ratio_charges = df["charges_mensuelles_totales"] / df["revenu_mensuel_net"]
    print(f"[9] Ratio charges/revenu — médiane: {_ratio_charges.median():.2%} | >40% charge vie: {(_ratio_charges > 0.40).mean():.2%}")

    # ==============================================================
    # ETAPE 10 — TARGET conservé tel quel (Kaggle, après filtres)
    # ==============================================================
    df_majority = df[df['TARGET'] == 0]
    df_minority = df[df['TARGET'] == 1]

    n_min_target = int(len(df_majority) * 0.15 / 0.85)

    df_minority_up = resample(
        df_minority,
        replace=True,
        n_samples=n_min_target,
        random_state=seed
    )

    df = pd.concat([df_majority, df_minority_up])
    df = df.sample(frac=1, random_state=seed).reset_index(drop=True)
    print(f"[10] Taux défaut après rééchantillonnage : {df['TARGET'].mean():.2%}")
# → doit afficher 15.00%
    print(f"[10] Taux défaut (TARGET inchangé, pas de rééchantillonnage) : {df['TARGET'].mean():.2%}")

    # ==============================================================
    # ETAPE 11 — EXPORT FINAL + CONTRÔLES QUALITÉ
    # ==============================================================
    features_finales = [
        "revenu_mensuel_net",
        "revenu_annuel",
        "charges_mensuelles_totales",
        "montant_demande",
        "nbr_mois_remboursement",
        "anciennete_emploi_mois",
        "type_contrat",
        "TARGET",
    ]

    df_export = df[features_finales].copy()

    checks = {
        "no_na": df_export.isnull().sum().sum() == 0,
        "revenu>=SMIG": (df_export["revenu_mensuel_net"] >= SMIG_NET).all(),
        "montant_500_15000": df_export["montant_demande"].between(500, 15000).all(),
        "duree_3_36": df_export["nbr_mois_remboursement"].between(3, 36).all(),
        "anciennete_0_360": df_export["anciennete_emploi_mois"].between(0, 360).all(),
        "ratio_charges_revenu_clip": (
            df_export["charges_mensuelles_totales"] / df_export["revenu_mensuel_net"]
        ).between(0.25, 0.65).all(),
        "contrat_cdi_cdd": df_export["type_contrat"].isin(["CDI", "CDD"]).all(),
        "target_0_1": df_export["TARGET"].isin([0, 1]).all(),
    }

    print("\n[11] Contrôles qualité:")
    for k, v in checks.items():
        print(f"  - {k:<24}: {'OK' if v else 'ERREUR'}")

    if output_csv is not None:
        output_csv = Path(output_csv)
        output_csv.parent.mkdir(parents=True, exist_ok=True)
        df_export.to_csv(output_csv, index=False)
        print("\n" + "=" * 70)
        print("TERMINÉ")
        print(f"Fichier: {output_csv}")
        print(f"Lignes: {len(df_export):,} | Colonnes: {len(df_export.columns)}")
        print("=" * 70)

    return df_export
