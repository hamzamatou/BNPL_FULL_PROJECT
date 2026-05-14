"""
Prescoring BNPL : bundle joblib (train_GBMlight + merge train_GBMlight_isoforest + SHAP).
Reutilise preprocess_full et alertes metier (bnpl-data-pipeline). Reponse JSON volontairement courte.
"""
from __future__ import annotations

import sys
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd

from app.config import Settings

_REQUIRED = (
    "revenu_mensuel_net",
    "revenu_annuel",
    "charges_mensuelles_totales",
    "montant_demande",
    "nbr_mois_remboursement",
    "anciennete_emploi_mois",
    "type_contrat",
)

_meta: dict[str, Any] | None = None
_meta_load_error: str | None = None


def _bootstrap_pipeline(pipeline_dir: Path) -> None:
    p = str(pipeline_dir.resolve())
    if p not in sys.path:
        sys.path.insert(0, p)


def get_prescoring_status() -> dict[str, Any]:
    """Pour GET /prescoring/ready : bundle charge ou message d'erreur."""
    global _meta, _meta_load_error
    if _meta is not None:
        return {"ready": True, "model_path": Settings.BNPL_MODEL_PATH, "error": None}
    if _meta_load_error:
        return {"ready": False, "model_path": Settings.BNPL_MODEL_PATH, "error": _meta_load_error}
    path = Path(Settings.BNPL_MODEL_PATH)
    if not path.is_file():
        return {"ready": False, "model_path": str(path.resolve()), "error": "Fichier .pkl introuvable"}
    return {"ready": True, "model_path": str(path.resolve()), "error": None}


def _load_meta() -> dict[str, Any]:
    global _meta, _meta_load_error
    if _meta is not None:
        return _meta
    path = Path(Settings.BNPL_MODEL_PATH)
    if not path.is_file():
        raise FileNotFoundError(f"Bundle modele introuvable : {path.resolve()}")
    _bootstrap_pipeline(Path(Settings.BNPL_PIPELINE_DIR))
    try:
        _meta = joblib.load(path)
        _meta_load_error = None
    except Exception as e:
        _meta_load_error = str(e)
        raise
    return _meta


def _parse_row(body: dict[str, Any]) -> dict[str, Any]:
    miss = []
    for k in _REQUIRED:
        v = body.get(k)
        if v is None or (isinstance(v, str) and not str(v).strip()):
            miss.append(k)
    if miss:
        raise ValueError(f"Champs manquants ou vides : {miss}")
    out: dict[str, Any] = {}
    for k in _REQUIRED:
        if k == "type_contrat":
            out[k] = str(body[k]).strip()
            continue
        try:
            out[k] = float(body[k])
        except (TypeError, ValueError) as e:
            raise ValueError(f"Valeur numerique invalide pour {k!r}") from e
    if out["montant_demande"] <= 0:
        raise ValueError("montant_demande doit etre > 0")
    if out["nbr_mois_remboursement"] <= 0:
        raise ValueError("nbr_mois_remboursement doit etre > 0")
    for k in ("revenu_mensuel_net", "revenu_annuel", "charges_mensuelles_totales", "anciennete_emploi_mois"):
        if out[k] < 0:
            raise ValueError(f"{k} doit etre >= 0")
    return out


def _zone_selon_pd(p: float) -> dict[str, Any]:
    """
    Feu tricolore sur la PD (probabilite de defaut), en proportion 0-1.
    Vert : PD de 0 % a 30 % inclus.
    Orange : PD strictement au-dessus de 30 % jusqu'a 60 % inclus.
    Rouge : PD au-dessus de 60 %.
    """
    pd_pct = float(p) * 100.0
    if pd_pct <= 30.0:
        return {
            "code": "vert",
            "couleur": "#16a34a",
            "libelle": "Zone verte (PD 0 % - 30 %)",
        }
    if pd_pct <= 60.0:
        return {
            "code": "orange",
            "couleur": "#ea580c",
            "libelle": "Zone orange (PD > 30 % - 60 %)",
        }
    return {
        "code": "rouge",
        "couleur": "#dc2626",
        "libelle": "Zone rouge (PD > 60 %)",
    }


def _explications_texte_analyste(
    foret: dict[str, Any] | None,
    zone: dict[str, Any],
    top: list[dict[str, Any]] | None,
    row_dict: dict[str, Any],
    p: float,
    pred: int,
    threshold: float,
    score_1000: int,
    shap_ok: bool,
) -> list[str]:
    """Phrases continues pour affichage analyste (foret, zone, synthese SHAP predict_manual)."""
    from predict_manual import (  # noqa: WPS433
        _analyst_credit_lines,
        interpret_risk,
    )

    lines: list[str] = []
    if foret is not None:
        if foret.get("atypique"):
            lines.append(
                "Foret d'isolation : le dossier est classe ATYPIQUE (eloigne des profils "
                "courants du jeu d'ajustement — a croiser avec pieces et coherence)."
            )
        else:
            lines.append(
                "Foret d'isolation : le dossier est classe TYPIQUE (proche des profils "
                "habituels du jeu d'ajustement)."
            )
        lines.append(
            f"Indicateur technique foret (score echantillon sklearn) : {foret.get('score_echantillon')}."
        )
    else:
        lines.append(
            "Foret d'isolation : non presente dans le bundle modele — aucun signal atypie/automatique associe."
        )

    lines.append(
        f"Repere score / zone PD : {score_1000}/1000 points — {zone.get('libelle', '')} "
        f"(code {zone.get('code', '')}), PD estimee {p:.1%}, seuil operationnel {threshold:.1%}."
    )
    q_title, q_detail, vs_seuil = interpret_risk(p, threshold)
    lines.append(f"Niveau de risque qualitatif : {q_title}. {q_detail}")
    lines.append(f"Lecture par rapport au seuil : {vs_seuil}")

    if shap_ok and top:
        lines.append("Principaux leviers du modele sur cette decision (SHAP, classe defaut = 1) :")
        for ln in _analyst_credit_lines(top, row_dict, p, pred, threshold, score_1000, k_summary=5):
            s = ln.strip()
            if not s:
                continue
            # Eviter de repeter l'en-tete decoratif et les lignes deja couvertes ci-dessus
            if s.startswith("---"):
                continue
            if s.startswith("Decision modele"):
                continue
            if "Les indicateurs ci-dessous" in s or "et bornage sur l'historique" in s:
                continue
            if s.startswith("[Precision]"):
                continue
            if s.startswith("A croiser :"):
                continue
            if s.startswith("[Note]"):
                lines.append("Note : explication statistique sur historique d'entrainement — pas un motif juridique de refus seul.")
                continue
            lines.append(s)
    elif shap_ok:
        lines.append(
            "Detail SHAP : explainer present mais aucun facteur retourne (cas technique improbable)."
        )
    else:
        lines.append(
            "Detail des contributions SHAP : non disponible (explainer absent du bundle ou librairie shap non installee)."
        )

    return lines


def prescore_dossier(body: dict[str, Any]) -> dict[str, Any]:
    """
    Ordre JSON : foret, pd_pct, score, zone (vert/orange/rouge selon PD %), alertes, explications (liste de phrases analyste),
    puis defaut et seuil_pd_pct.
    """
    meta = _load_meta()
    row_dict = _parse_row(body)

    from predict_manual import _business_alerts  # noqa: WPS433
    from shap_tools import SHAP_AVAILABLE, top_shap_tree
    from test_rest_dataset import preprocess_full

    model = meta["model"]
    threshold = float(meta.get("threshold", 0.5))

    df = pd.DataFrame([row_dict])
    X, _ = preprocess_full(df, meta)

    # 1) Foret d'isolation (meme X que le GBM)
    foret: dict[str, Any] | None = None
    iso = meta.get("isolation_forest")
    sc = meta.get("if_scaler")
    if iso is not None and sc is not None:
        Xi = sc.transform(X.values)
        pred_if = int(iso.predict(Xi)[0])
        ss = float(iso.score_samples(Xi)[0])
        foret = {
            "atypique": pred_if == -1,
            "score_echantillon": round(ss, 4),
            "predict_sklearn": pred_if,
        }

    p = float(model.predict_proba(X)[0, 1])
    pred = int(p >= threshold)
    score_1000 = int(round(1000.0 * (1.0 - float(np.clip(p, 0.0, 1.0)))))
    alerts = _business_alerts(row_dict)
    zone = _zone_selon_pd(p)

    top: list[dict[str, Any]] | None = None
    shap_ok = bool(meta.get("shap_explainer") is not None and SHAP_AVAILABLE)
    if shap_ok:
        expl = meta["shap_explainer"]
        feats = meta.get("features") or list(X.columns)
        top = top_shap_tree(expl, X, feats, k=5)

    explications = _explications_texte_analyste(
        foret, zone, top, row_dict, p, pred, threshold, score_1000, shap_ok
    )

    # Ordre des cles = ordre de lecture souhaite
    return {
        "foret": foret,
        "pd_pct": round(p * 100.0, 2),
        "score": score_1000,
        "zone": zone,
        "alertes": alerts,
        "explications": explications,
        "defaut": bool(pred),
        "seuil_pd_pct": round(threshold * 100.0, 2),
    }
