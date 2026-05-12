"""
Tests manuels d'inference BNPL (une ou plusieurs lignes) sans re-entrainer.

  python predict_manual.py --demo
  python predict_manual.py --json profils.json
  python predict_manual.py --revenu_mensuel_net 2200 --revenu_annuel 26400 \\
      --charges_mensuelles_totales 750 --montant_demande 3000 \\
      --nbr_mois_remboursement 12 --anciennete_emploi_mois 48 --type_contrat CDI

Le pipeline (clean, FE, clip, encodage, features) est le meme que test_rest_dataset.py.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import joblib
import pandas as pd

from test_rest_dataset import DEFAULT_MODEL, preprocess_full

NUM_FIELDS = (
    "revenu_mensuel_net",
    "revenu_annuel",
    "charges_mensuelles_totales",
    "montant_demande",
    "nbr_mois_remboursement",
    "anciennete_emploi_mois",
)


def interpret_risk(proba_defaut: float, threshold: float) -> tuple[str, str, str]:
    """
    Interpretation alignee sur le seuil operationnel du .pkl + echelle qualitative
    (les anciens seuils fixes 25%/45% disaient « solide » pour ~16% de risque, ce qui trompe).
    """
    p = proba_defaut
    t = max(threshold, 1e-9)

    if p < 0.08:
        qual = "Risque estime faible", "P(defaut) sous 8% (hors modele, lecture metier)"
    elif p < 0.15:
        qual = "Risque estime modere", "Entre 8% et 15% - deja significatif pour du credit"
    elif p < 0.25:
        qual = "Risque estime notable", "Entre 15% et 25%"
    elif p < 0.40:
        qual = "Risque estime eleve", "Entre 25% et 40%"
    else:
        qual = "Risque estime tres eleve", "Au-dela de 40%"

    if p >= t:
        vs_seuil = f"Au-dessus du seuil operationnel ({t:.1%}) -> le score classe en defaut"
    elif p >= 0.95 * t:
        vs_seuil = f"Tres proche du seuil ({t:.1%}): marge faible malgre classe non-defaut"
    elif p >= 0.70 * t:
        vs_seuil = f"Sous le seuil ({t:.1%}) avec marge moderee"
    else:
        vs_seuil = f"Nettement sous le seuil ({t:.1%})"

    return qual[0], qual[1], vs_seuil


DEMOS: list[dict] = [
    {
        "libelle": "CDI stable - 2200 DT",
        "revenu_mensuel_net": 2200.0,
        "revenu_annuel": 26400.0,
        "charges_mensuelles_totales": 750.0,
        "montant_demande": 3000.0,
        "nbr_mois_remboursement": 12.0,
        "anciennete_emploi_mois": 48.0,
        "type_contrat": "CDI",
    },
    {
        "libelle": "CDD charge elevee - 900 DT",
        "revenu_mensuel_net": 900.0,
        "revenu_annuel": 10800.0,
        "charges_mensuelles_totales": 620.0,
        "montant_demande": 2500.0,
        "nbr_mois_remboursement": 6.0,
        "anciennete_emploi_mois": 8.0,
        "type_contrat": "CDD",
    },
    {
        "libelle": "CDI moyen - 1500 DT",
        "revenu_mensuel_net": 1500.0,
        "revenu_annuel": 18000.0,
        "charges_mensuelles_totales": 550.0,
        "montant_demande": 2000.0,
        "nbr_mois_remboursement": 12.0,
        "anciennete_emploi_mois": 36.0,
        "type_contrat": "CDI",
    },
]


def _row_from_cli(ns: argparse.Namespace) -> dict:
    vals = {k: getattr(ns, k) for k in NUM_FIELDS}
    tc = ns.type_contrat
    n_filled = sum(v is not None for v in vals.values()) + (1 if tc is not None else 0)
    if n_filled == 0:
        return {}
    if n_filled != 7 or tc is None:
        raise SystemExit(
            "Pour une ligne en CLI, renseignez les 7 champs numeriques + --type_contrat "
            "(ou utilisez --demo / --json)."
        )
    out = {k: float(vals[k]) for k in NUM_FIELDS}
    out["type_contrat"] = str(tc)
    return out


def _rows_from_json(path: Path) -> list[tuple[str | None, dict]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(raw, dict):
        raw = [raw]
    rows: list[tuple[str | None, dict]] = []
    for i, obj in enumerate(raw):
        if not isinstance(obj, dict):
            raise ValueError(f"Element {i} : objet JSON attendu")
        label = obj.pop("libelle", None) or obj.pop("label", None) or obj.pop("profil", None)
        miss = [k for k in NUM_FIELDS if k not in obj]
        if miss:
            raise ValueError(f"Element {i} : champs manquants {miss}")
        if "type_contrat" not in obj:
            raise ValueError(f"Element {i} : type_contrat manquant")
        row = {k: float(obj[k]) for k in NUM_FIELDS}
        row["type_contrat"] = str(obj["type_contrat"])
        rows.append((label if isinstance(label, str) else None, row))
    return rows


def _business_alerts(row_dict: dict) -> list[str]:
    """
    Regles simples sur les entrees brutes (avant clip / arbre).
    Le score ML est une moyenne empirique sur des profils ressemblants, pas une 'logique' a 100%.
    """
    alerts: list[str] = []
    r = float(row_dict["revenu_mensuel_net"])
    c = float(row_dict["charges_mensuelles_totales"])
    m = float(row_dict["montant_demande"])
    n = float(row_dict["nbr_mois_remboursement"])
    ann = float(row_dict["revenu_annuel"])
    mens_bnpl = m / (n + 1.0)
    rav = r - c
    cap = r - c - mens_bnpl

    if c >= r:
        alerts.append(
            "REFUS METIER RECOMMANDE: charges mensuelles >= revenu mensuel net "
            f"(reste a vivre avant BNPL = {rav:.0f}). "
            "Le modele peut rester autour de 50-60% de P(defaut): il calibre sur l'historique "
            "(defauts rares meme parmi les profils stresses) et les extremes sont souvent plafonnes "
            "par le clip d'entrainement - ce n'est pas une probabilite 'morale' de defaut."
        )
    elif rav < mens_bnpl:
        alerts.append(
            "ALERTE METIER: apres mensualite BNPL estimee, la capacite nette approximative "
            f"est negative (revenu {r:.0f} - charges {c:.0f} - mens. BNPL ~{mens_bnpl:.0f} = {cap:.0f}). "
            "Envisager refus ou analyse manuelle en complement du score."
        )

    if r > 0:
        attendu = 12.0 * r
        ecart = abs(ann - attendu) / attendu
        if ecart > 0.25:
            alerts.append(
                f"COHERENCE: revenu_annuel ({ann:.0f}) s'ecarte de plus de 25% de 12 x mensuel ({attendu:.0f}). "
                "Des entrees contradictoires degradent l'interpretation du score."
            )

    effort = (c + mens_bnpl) / (r + 1.0)
    if effort >= 0.80:
        alerts.append(
            f"ALERTE METIER: charges + mensualite BNPL estimee (~{mens_bnpl:.0f}) = "
            f"{effort:.0%} du revenu mensuel - tres tendu. Une PD ML basse peut sous-estimer le risque "
            "operationnel (CDD, aleas, imprevus)."
        )

    contrat = str(row_dict.get("type_contrat", "")).strip().upper()
    age_m = float(row_dict["anciennete_emploi_mois"])
    if contrat == "CDD" and age_m < 12:
        alerts.append(
            f"ALERTE METIER: CDD avec seulement {age_m:.0f} mois d'anciennete - instabilite d'emploi "
            "mal resumee par des ratios 'encore positifs'. Le score peut paraitre trop optimiste; "
            "regles internes (duree minimale, file active) souvent necessaires."
        )

    return alerts


def predict_rows(meta: dict, rows: list[tuple[str | None, dict]]) -> None:
    model = meta["model"]
    threshold = float(meta.get("threshold", 0.5))
    print(f"Modele : seuil operationnel du .pkl = {threshold:.6f}")
    print("(Echelle qualitative indicative; la decision automatique du modele est la ligne 'Classe'.)\n")

    for label, row_dict in rows:
        df = pd.DataFrame([row_dict])
        X, _ = preprocess_full(df, meta)
        p = float(model.predict_proba(X)[0, 1])
        pred = int(p >= threshold)
        q_title, q_detail, vs_seuil = interpret_risk(p, threshold)
        title = label or "Profil (CLI)"
        print(f"  {title}")

        biz = _business_alerts(row_dict)
        if biz:
            print("    --- Alertes metier (entrees brutes, hors score ML) ---")
            for line in biz:
                print(f"    ! {line}")
            print()

        print(f"    P(defaut)              : {p:.2%}")
        print(f"    Classe (seuil .pkl)      : {'defaut' if pred else 'non-defaut'} (seuil = {threshold:.4f})")
        print(f"    Niveau qualitatif      : {q_title} - {q_detail}")
        print(f"    Lecture vs seuil .pkl  : {vs_seuil}")
        print()


def main() -> int:
    parser = argparse.ArgumentParser(description="Inference manuelle BNPL (profils uniques)")
    parser.add_argument("-m", "--model", type=Path, default=DEFAULT_MODEL, help="Fichier .pkl")
    parser.add_argument("--demo", action="store_true", help="Trois profils d'exemple")
    parser.add_argument("--json", type=Path, metavar="FILE", help="JSON : un objet ou une liste d'objets")

    for k in NUM_FIELDS:
        parser.add_argument(f"--{k}", type=float, default=None)
    parser.add_argument("--type_contrat", type=str, default=None)

    args = parser.parse_args()

    if not args.model.is_file():
        print(f"Modele introuvable : {args.model.resolve()}", file=sys.stderr)
        return 1

    meta = joblib.load(args.model)

    rows: list[tuple[str | None, dict]] = []

    if args.demo:
        for d in DEMOS:
            dd = dict(d)
            lib = dd.pop("libelle")
            rows.append((lib, dd))
    elif args.json is not None:
        if not args.json.is_file():
            print(f"JSON introuvable : {args.json.resolve()}", file=sys.stderr)
            return 1
        try:
            rows = _rows_from_json(args.json)
        except (json.JSONDecodeError, ValueError) as e:
            print(str(e), file=sys.stderr)
            return 1
    else:
        one = _row_from_cli(args)
        if not one:
            parser.print_help()
            print("\nExemple : python predict_manual.py --demo")
            return 0
        rows = [(None, one)]

    predict_rows(meta, rows)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())