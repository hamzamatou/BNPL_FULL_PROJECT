"""
Explications prescoring via la librairie SHAP (TreeExplainer ou explainer du bundle).
"""
from __future__ import annotations

import logging
from typing import Any

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

_FEATURE_LABELS: dict[str, str] = {
    "buffer_financier": "Marge financière (buffer)",
    "stress_financier": "Stress financier",
    "reste_a_vivre": "Reste à vivre",
    "mensualite_bnpl": "Mensualité BNPL",
    "nbr_mois_remboursement": "Durée de remboursement (mois)",
    "log_montant": "Montant demandé (log)",
    "montant_demande": "Montant demandé",
    "revenu_mensuel_net": "Revenu mensuel net",
    "charges_mensuelles_totales": "Charges mensuelles totales",
    "anciennete_emploi_mois": "Ancienneté emploi (mois)",
    "pression_endettement_jeune_anciennete": "Pression endettement / ancienneté",
    "score_risque_combine": "Score risque combiné",
    "cdd_tenure_inverse_risque": "Risque CDD / ancienneté",
    "flag_cdd_anciennete_faible": "CDD avec faible ancienneté",
    "interaction_cdd_taux_endettement": "Interaction CDD × endettement",
    "type_contrat": "Type de contrat",
}


def shap_available() -> bool:
    try:
        import shap  # noqa: F401
        return True
    except ImportError:
        return False


def _label_feature(name: str) -> str:
    return _FEATURE_LABELS.get(name, name.replace("_", " "))


# Seuil interne (non expose dans le JSON) + top N pour la synthese analyste.
SHAP_MIN_ABS = 0.005
SHAP_TOP_N = 3


def _join_labels(labels: list[str]) -> str:
    if not labels:
        return ""
    if len(labels) == 1:
        return labels[0]
    if len(labels) == 2:
        return f"{labels[0]} et {labels[1]}"
    return f"{labels[0]}, {labels[1]} et {labels[2]}"


def _top_feature_labels(
    shap_values: dict[str, float],
    *,
    positive: bool,
    top_n: int = SHAP_TOP_N,
    min_abs: float = SHAP_MIN_ABS,
) -> list[str]:
    picked = [
        (name, val)
        for name, val in shap_values.items()
        if (val > 0 if positive else val < 0) and abs(val) >= min_abs
    ]
    picked.sort(key=lambda x: abs(x[1]), reverse=True)
    return [_label_feature(name) for name, _ in picked[:top_n]]


def generate_leviers_score(shap_values: dict[str, float] | None) -> str | None:
    """
    Synthese courte (1–2 phrases max) des leviers hausse/baisse, sans chiffres SHAP.
    """
    if not shap_values:
        return None

    hausse = _top_feature_labels(shap_values, positive=True)
    baisse = _top_feature_labels(shap_values, positive=False)

    if not hausse and not baisse:
        return "Leviers du score : aucun facteur majeur identifie sur ce dossier."

    if hausse and baisse:
        return (
            f"Le score est tire vers le haut par {_join_labels(hausse)} ; "
            f"il est limite par {_join_labels(baisse)}."
        )
    if hausse:
        return f"Le score est surtout tire vers le haut par {_join_labels(hausse)}."
    return f"Le score est surtout limite par {_join_labels(baisse)}."


def generate_explanation(shap_values: dict[str, float]) -> str:
    """Alias retro-compat : renvoie la synthese courte (plus de liste SHAP detaillee)."""
    text = generate_leviers_score(shap_values)
    return text or "Leviers du score : aucun facteur majeur identifie sur ce dossier."


def _unwrap_lightgbm_estimator(model: Any) -> Any | None:
    """Extrait le LGBMClassifier sous-jacent (CalibratedClassifierCV, etc.)."""
    m = model
    if m is None:
        return None

    if hasattr(m, "calibrated_classifiers_") and m.calibrated_classifiers_:
        try:
            m = m.calibrated_classifiers_[0].estimator
        except (IndexError, AttributeError):
            pass

    for _ in range(4):
        if hasattr(m, "booster_") or type(m).__name__ in ("LGBMClassifier", "LGBMRegressor"):
            return m
        if hasattr(m, "estimator"):
            m = m.estimator
            continue
        if hasattr(m, "base_estimator"):
            m = m.base_estimator
            continue
        break

    return m if hasattr(m, "booster_") else None


def _dict_from_shap_row(row: np.ndarray, feature_names: list[str]) -> dict[str, float]:
    out: dict[str, float] = {}
    row = np.asarray(row, dtype=float).ravel()
    for i, name in enumerate(feature_names):
        if i >= len(row):
            break
        val = float(row[i])
        if np.isfinite(val) and abs(val) >= 1e-12:
            out[name] = val
    return out


def _select_default_class_shap(shap_values: Any) -> np.ndarray:
    """
    Normalise la sortie SHAP (API legacy liste ou Explanation récente)
    vers un vecteur 1D pour la classe « défaut » (index 1 si binaire).
    """
    if isinstance(shap_values, list):
        picked = shap_values[1] if len(shap_values) > 1 else shap_values[0]
        arr = np.asarray(picked.values if hasattr(picked, "values") else picked)
    elif hasattr(shap_values, "values"):
        arr = np.asarray(shap_values.values)
    else:
        arr = np.asarray(shap_values)

    if arr.ndim == 3:
        return arr[0, :, 1] if arr.shape[2] > 1 else arr[0, :, 0]
    if arr.ndim == 2:
        return arr[0]
    return arr.ravel()


def _compute_shap_with_explainer(
    explainer: Any,
    X: pd.DataFrame,
    feature_names: list[str],
) -> dict[str, float] | None:
    import shap

    try:
        # API SHAP >= 0.40 : explainer(X) ; legacy : explainer.shap_values(X)
        if hasattr(explainer, "shap_values"):
            raw = explainer.shap_values(X)
        else:
            raw = explainer(X)
    except Exception as ex:
        logger.warning("SHAP explainer.shap_values / __call__ a échoué : %s", ex)
        return None

    try:
        row = _select_default_class_shap(raw)
        return _dict_from_shap_row(row, feature_names) or None
    except Exception as ex:
        logger.warning("Parsing sortie SHAP impossible : %s", ex)
        return None


def _build_tree_explainer(lgbm: Any, X_background: pd.DataFrame | None) -> Any:
    import shap

    if X_background is not None and not X_background.empty:
        return shap.TreeExplainer(lgbm, data=X_background)
    return shap.TreeExplainer(lgbm)


def compute_shap_values_dict(
    model: Any,
    X: pd.DataFrame,
    feature_names: list[str],
    bundle_explainer: Any | None = None,
) -> dict[str, float] | None:
    """
    Valeurs SHAP par variable (classe défaut) via la librairie shap uniquement.
    """
    if not shap_available():
        logger.error("Librairie shap absente — pip install shap>=0.44")
        return None

    if X is None or X.empty or not feature_names:
        return None

    X_use = X.reindex(columns=feature_names, fill_value=0.0)

    # 1) Explainer sérialisé dans le bundle .pkl (entraînement)
    if bundle_explainer is not None:
        result = _compute_shap_with_explainer(bundle_explainer, X_use, feature_names)
        if result:
            return result
        logger.info("Explainer bundle présent mais SHAP vide — repli TreeExplainer.")

    # 2) TreeExplainer sur le LightGBM du modèle calibré
    lgbm = _unwrap_lightgbm_estimator(model)
    if lgbm is not None:
        try:
            tree_explainer = _build_tree_explainer(lgbm, X_use)
            result = _compute_shap_with_explainer(tree_explainer, X_use, feature_names)
            if result:
                return result
        except Exception as ex:
            logger.warning("TreeExplainer SHAP a échoué : %s", ex)

    return None
