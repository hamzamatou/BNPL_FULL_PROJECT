import sys
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd

PACKAGE_DIR = Path(__file__).resolve().parent
COLONNE = "NAME_INCOME_TYPE"
path_dataset = PACKAGE_DIR / "data" / "application_train.csv"
out_dir = PACKAGE_DIR / "out"
chart_path = out_dir / "types_revenu.png"

GROUPES = {
    "Working": "CDI",
    "State servant": "CDI",
    "Commercial associate": "CDD",
}
GROUPE_DEFAUT = "Exclu"

COULEURS = {
    "CDI": "#16a34a",
    "CDD": "#2563eb",
    "Exclu": "#9ca3af",
}

ORDRE_GROUPES = ["CDI", "CDD", "Exclu"]

if not path_dataset.is_file():
    print(f"Erreur: fichier introuvable : {path_dataset}", file=sys.stderr)
    print("Téléchargez application_train.csv sur Kaggle et copiez-le dans data/", file=sys.stderr)
    print("https://www.kaggle.com/c/home-credit-default-risk/data", file=sys.stderr)
    raise SystemExit(1)

df = pd.read_csv(path_dataset, usecols=[COLONNE])
serie = df[COLONNE]

effectifs = serie.value_counts(dropna=False)
pourcentages = (effectifs / len(serie) * 100).round(2)

tableau = pd.DataFrame({
    "effectif": effectifs,
    "pourcentage": pourcentages,
})
tableau["groupe"] = tableau.index.map(lambda v: GROUPES.get(v, GROUPE_DEFAUT))

tableau["__ordre_groupe"] = tableau["groupe"].map(
    {g: i for i, g in enumerate(ORDRE_GROUPES)}
)
tableau = tableau.sort_values(
    by=["__ordre_groupe", "effectif"],
    ascending=[True, False],
).drop(columns="__ordre_groupe")

print(f"=== {COLONNE} dans le dataset ===\n")
print(f"Nombre total de lignes : {len(serie):,}")
print(f"Nombre de types distincts : {serie.nunique()}\n")

print("=== Répartition par groupe de contrat ===")
synthese = tableau.groupby("groupe")["effectif"].sum().reindex(ORDRE_GROUPES)
synthese_pct = (synthese / len(serie) * 100).round(2)
for g in ORDRE_GROUPES:
    print(f"  {g:6s} : {synthese[g]:>8,}  ({synthese_pct[g]:5.2f}%)")

print("\n=== Détail par type de revenu ===")
print(tableau.to_string())

out_dir.mkdir(parents=True, exist_ok=True)

tableau_plot = tableau.iloc[::-1]
positions = range(len(tableau_plot))
couleurs_barres = [COULEURS[g] for g in tableau_plot["groupe"]]
labels_y = [f"[{g}] {idx}" for idx, g in zip(tableau_plot.index, tableau_plot["groupe"])]

fig, ax = plt.subplots(figsize=(11, 6))
ax.barh(list(positions), tableau_plot["effectif"], color=couleurs_barres, edgecolor="white")
ax.set_yticks(list(positions))
ax.set_yticklabels(labels_y)
ax.set_title(f"Types de revenu groupés par contrat ({COLONNE})")
ax.set_xlabel("Nombre de dossiers")
ax.set_ylabel("Groupe contrat / Type de revenu")

for pos, (eff, pct) in enumerate(zip(tableau_plot["effectif"], tableau_plot["pourcentage"])):
    ax.text(eff, pos, f"  {eff:,} ({pct}%)", va="center", fontsize=9)

handles = [plt.Rectangle((0, 0), 1, 1, color=COULEURS[g]) for g in ORDRE_GROUPES]
ax.legend(handles, ORDRE_GROUPES, title="Groupe contrat", loc="lower right")

plt.tight_layout()
fig.savefig(chart_path, dpi=120)
print(f"\nGraphique enregistré : {chart_path}")
plt.show()
