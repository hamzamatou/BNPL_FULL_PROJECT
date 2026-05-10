"""
Même entraînement que train_logistic.py ; fichier modèle par défaut : logistic_bnpl_seuil.joblib.

Usage :
  python train_logistic_seuil.py
  python train_logistic_seuil.py -i .\\out\\dataset_bnpl_tunisien_cleanV3.csv
"""

from __future__ import annotations

import runpy
import sys
from pathlib import Path

PACKAGE_DIR = Path(__file__).resolve().parent

if __name__ == "__main__":
    argv = sys.argv[:]
    if "-o" not in argv and "--model-out" not in argv:
        argv.append("-o")
        argv.append(str(PACKAGE_DIR / "out" / "models" / "logistic_bnpl_seuil.joblib"))
    sys.argv = argv
    runpy.run_path(str(PACKAGE_DIR / "train_logistic.py"), run_name="__main__")
