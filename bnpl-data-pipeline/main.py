"""
Point d'entrée : prépare le CSV BNPL à partir de application_train.csv (Kaggle).

Usage:
  python main.py
  python main.py -i \"chemin/application_train.csv\" -o \"chemin/sortie.csv\"

Placez application_train.csv dans le dossier data/ du projet ou passez --input explicite.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from prepare_data import prepare_bnpl_dataset

PACKAGE_DIR = Path(__file__).resolve().parent
DEFAULT_INPUT = PACKAGE_DIR / "data" / "application_train.csv"
DEFAULT_OUTPUT = PACKAGE_DIR / "out" / "dataset_bnpl_tunisien_cleanV3.csv"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Pipeline nettoyage Home Credit → BNPL Tunisien")
    parser.add_argument(
        "-i",
        "--input",
        type=Path,
        default=DEFAULT_INPUT,
        help=f"CSV Kaggle application_train.csv (défaut: {DEFAULT_INPUT})",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"CSV nettoyé en sortie (défaut: {DEFAULT_OUTPUT})",
    )
    parser.add_argument("--seed", type=int, default=42, help="Graine RNG (charges simulées)")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not Path(args.input).is_file():
        print(f"Erreur: fichier introuvable : {args.input}", file=sys.stderr)
        print(f"Copiez application_train.csv depuis Kaggle dans : {DEFAULT_INPUT.parent}", file=sys.stderr)
        return 1
    prepare_bnpl_dataset(Path(args.input), Path(args.output), seed=args.seed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
