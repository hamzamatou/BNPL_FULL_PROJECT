"""
Genere out/augment_bnpl_risk_profiles.csv : lignes supplementaires (profils tendus)
pour augmenter l'apprentissage du risque de defaut — meme schema que prepare_data (8 colonnes).

  python build_bnpl_augment.py
  python merge_bnpl_augment.py

Les lignes melangent CDD + faible anciennete + durees courtes + charges elevees,
avec une proportion elevee de TARGET=1 (defaut) pour que le modele puisse monter la PD.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

PACKAGE_DIR = Path(__file__).resolve().parent
OUT_AUGMENT = PACKAGE_DIR / "out" / "augment_bnpl_risk_profiles.csv"

SMIG = 554.736


def _row(
    r: float,
    charges: float,
    montant: float,
    n_mois: int,
    anc: int,
    tc: str,
    target: int,
) -> dict:
    ra = round(r * 12.0, 0)
    return {
        "revenu_mensuel_net": round(r, 0),
        "revenu_annuel": ra,
        "charges_mensuelles_totales": round(charges, 0),
        "montant_demande": round(montant, 0),
        "nbr_mois_remboursement": int(n_mois),
        "anciennete_emploi_mois": int(anc),
        "type_contrat": tc,
        "TARGET": int(target),
    }


def build_fixed_profiles() -> list[dict]:
    """Profils metier explicites (reproductibles)."""
    rows: list[dict] = []
    # Defauts : charges >= revenu ou CDD tres jeune + pret court + gros montant
    rows += [
        _row(1200, 2250, 3000, 12, 48, "CDI", 1),
        _row(1200, 1200, 4000, 3, 3, "CDD", 1),
        _row(1300, 1400, 4500, 4, 2, "CDD", 1),
        _row(1100, 1150, 3500, 6, 4, "CDD", 1),
        _row(1500, 1600, 5000, 3, 5, "CDD", 1),
        _row(1000, 1050, 2800, 8, 1, "CDD", 1),
        _row(1800, 1900, 6000, 4, 6, "CDD", 1),
        _row(1400, 1450, 4000, 3, 3, "CDD", 1),
    ]
    # Non-defauts : meme famille mais capacite un peu positive (contre-exemples)
    rows += [
        _row(2200, 800, 2000, 12, 36, "CDI", 0),
        _row(2000, 900, 2500, 18, 24, "CDD", 0),
        _row(1600, 700, 1500, 12, 18, "CDD", 0),
        _row(2500, 1000, 3000, 24, 48, "CDI", 0),
    ]
    return rows


def build_random_block(n: int, seed: int = 42) -> list[dict]:
    rng = np.random.default_rng(seed)
    rows: list[dict] = []
    for _ in range(n):
        r = float(rng.choice([900, 1000, 1100, 1200, 1300, 1400, 1500, 1700, 2000]))
        r = max(r, SMIG + 50)
        cdd = rng.random() < 0.78
        tc = "CDD" if cdd else "CDI"
        anc = int(rng.integers(1, 10)) if cdd else int(rng.integers(12, 60))
        n_mois = int(rng.choice([3, 4, 5, 6, 8, 10, 12]))
        montant = float(rng.integers(2500, 9500))
        montant = float(np.clip(montant, 500, 15000))
        n_mois = int(np.clip(n_mois, 3, 36))
        anc = int(np.clip(anc, 0, 360))

        stress = rng.random()
        if stress < 0.62:
            charges = float(r * rng.uniform(0.72, 1.18))
            target = 1
        elif stress < 0.82:
            charges = float(r * rng.uniform(0.55, 0.72))
            target = int(rng.random() < 0.45)
        else:
            charges = float(r * rng.uniform(0.28, 0.52))
            target = int(rng.random() < 0.12)

        rows.append(_row(r, charges, montant, n_mois, anc, tc, target))
    return rows


def main() -> int:
    OUT_AUGMENT.parent.mkdir(parents=True, exist_ok=True)

    all_rows = build_fixed_profiles() + build_random_block(55, seed=43)
    df = pd.DataFrame(all_rows)
    df["nbr_mois_remboursement"] = df["nbr_mois_remboursement"].clip(3, 36).astype(int)
    df["montant_demande"] = df["montant_demande"].clip(500, 15000)
    df["anciennete_emploi_mois"] = df["anciennete_emploi_mois"].clip(0, 360).astype(int)
    df["type_contrat"] = df["type_contrat"].where(df["type_contrat"].isin(["CDI", "CDD"]), "CDD")

    df.to_csv(OUT_AUGMENT, index=False)
    print(f"Ecrit : {OUT_AUGMENT.resolve()} | lignes : {len(df)} | taux defaut : {df['TARGET'].mean():.1%}")
    print("Fusion avec le dataset principal : python merge_bnpl_augment.py")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
