"""
Concatene le dataset BNPL principal avec le fichier d'augmentation.

  python merge_bnpl_augment.py
  python merge_bnpl_augment.py -o out\\dataset_bnpl_tunisien_merged.csv

Par defaut :
  base    = out/dataset_bnpl_tunisien_cleanV3.csv
  augment = out/augment_bnpl_risk_profiles.csv
  sortie  = out/dataset_bnpl_tunisien_merged.csv
"""

from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

PACKAGE_DIR = Path(__file__).resolve().parent


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("-b", "--base", type=Path, default=PACKAGE_DIR / "out" / "dataset_bnpl_tunisien_cleanV3.csv")
    p.add_argument("-a", "--augment", type=Path, default=PACKAGE_DIR / "out" / "augment_bnpl_risk_profiles.csv")
    p.add_argument(
        "-o",
        "--output",
        type=Path,
        default=PACKAGE_DIR / "out" / "dataset_bnpl_tunisien_merged.csv",
    )
    args = p.parse_args()

    if not args.base.is_file():
        print(f"Base introuvable : {args.base}")
        return 1
    if not args.augment.is_file():
        print(f"Augment introuvable : {args.augment} — lancez d'abord : python build_bnpl_augment.py")
        return 1

    base = pd.read_csv(args.base)
    aug = pd.read_csv(args.augment)
    need = list(base.columns)
    miss = [c for c in need if c not in aug.columns]
    if miss:
        print(f"Colonnes manquantes dans augment : {miss}")
        return 1
    aug = aug[need].copy()

    out = pd.concat([base, aug], axis=0, ignore_index=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    out.to_csv(args.output, index=False)

    print(f"Base : {len(base):,} lignes")
    print(f"Augment : {len(aug):,} lignes")
    print(f"Sortie : {args.output.resolve()} | total : {len(out):,} | taux defaut : {out['TARGET'].mean():.2%}")
    print("\nEntrainement : pointez DATA_PATH / -i vers ce fichier merged.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
