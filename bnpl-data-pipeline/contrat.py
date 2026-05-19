import sys
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd

PACKAGE_DIR = Path(__file__).resolve().parent
COLONNE = "NAME_INCOME_TYPE"
path_dataset = PACKAGE_DIR / "data" / "application_train.csv"
out_dir = PACKAGE_DIR / "out"
chart_path = out_dir / "types_revenu.png"

if not path_dataset.is_file():
    print(f"Erreur: fichier introuvable : {path_dataset}", file=sys.stderr)
    print("Téléchargez application_train.csv sur Kaggle et copiez-le dans data/", file=sys.stderr)
    print("https://www.kaggle.com/c/home-credit-default-risk/data", file=sys.stderr)
    raise SystemExit(1)

df = pd.read_csv(path_dataset, usecols=[COLONNE])
serie = df[COLONNE]

effectifs = serie.value_counts(dropna=False)
pourcentages = (effectifs / len(serie) * 100).round(2)

print(f"=== {COLONNE} dans le dataset ===\n")
print(f"Nombre total de lignes : {len(serie):,}")
print(f"Nombre de types distincts : {serie.nunique()}\n")
print("Liste des types :")
for i, valeur in enumerate(serie.unique(), start=1):
    print(f"  {i}. {valeur}")

print("\n=== Répartition (effectifs et %) ===")
tableau = pd.DataFrame({"effectif": effectifs, "pourcentage": pourcentages})
print(tableau.to_string())

out_dir.mkdir(parents=True, exist_ok=True)
fig, ax = plt.subplots(figsize=(10, 6))
effectifs.sort_values().plot(kind="barh", ax=ax, color="#2563eb", edgecolor="white")
ax.set_title(f"Types de revenu ({COLONNE})")
ax.set_xlabel("Nombre de dossiers")
ax.set_ylabel("Type de revenu")
for i, (valeur, pct) in enumerate(zip(effectifs.sort_values().values, pourcentages.loc[effectifs.sort_values().index].values)):
    ax.text(valeur, i, f"  {valeur:,} ({pct}%)", va="center", fontsize=9)
plt.tight_layout()
fig.savefig(chart_path, dpi=120)
print(f"\nGraphique enregistré : {chart_path}")
plt.show()
