"""
Prescoring BNPL : bundle joblib (train_GBMlight + merge train_GBMlight_isoforest + SHAP).
Reutilise preprocess_full (bnpl-data-pipeline). Reponse JSON volontairement courte.
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


_DISCLAIMER = (
    "Indicateur statistique base sur l'historique d'entrainement — "
    "a croiser avec pieces et politique interne ; pas un motif juridique de refus seul."
)


def _explications_texte_analyste(
    foret: dict[str, Any] | None,
    leviers: str | None,
    *,
    shap_lib_ok: bool = True,
) -> list[str]:
    """Texte analyste : forêt, leviers du score (court), note conformite."""
    lines: list[str] = []
    if foret is not None:
        if foret.get("atypique"):
            lines.append(
                "Foret d'isolation : dossier atypique, eloigne des profils habituels "
                "— a croiser avec pieces et coherence."
            )
        else:
            lines.append(
                "Foret d'isolation : dossier typique, proche des profils habituels."
            )
    else:
        lines.append(
            "Foret d'isolation : non presente dans le bundle modele — "
            "aucun signal atypie/automatique associe."
        )

    if leviers:
        lines.append(leviers)
    elif not shap_lib_ok:
        lines.append(
            "Leviers du score : non disponibles (librairie shap absente du service)."
        )
    else:
        lines.append("Leviers du score : non disponibles pour ce dossier.")

    lines.append(_DISCLAIMER)
    return lines


def prescore_dossier(body: dict[str, Any]) -> dict[str, Any]:
    """
    Ordre JSON : foret, pd_pct, score, zone, explications, defaut, seuil_pd_pct.
    """
    meta = _load_meta()
    row_dict = _parse_row(body)

    from test_rest_dataset import preprocess_full

    from app.services.prescoring_explanations import (
        compute_shap_values_dict,
        generate_leviers_score,
        shap_available,
    )

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
    zone = _zone_selon_pd(p)

    feats = meta.get("features") or list(X.columns)
    shap_dict = compute_shap_values_dict(
        model,
        X,
        list(feats),
        bundle_explainer=meta.get("shap_explainer"),
    )
    leviers = generate_leviers_score(shap_dict) if shap_dict else None

    explications = _explications_texte_analyste(
        foret,
        leviers,
        shap_lib_ok=shap_available(),
    )

    # Ordre des cles = ordre de lecture souhaite
    return {
        "foret": foret,
        "pd_pct": round(p * 100.0, 2),
        "score": score_1000,
        "zone": zone,
        "explications": explications,
        "defaut": bool(pred),
        "seuil_pd_pct": round(threshold * 100.0, 2),
    }
