"""Tests calcul SHAP (mock TreeExplainer)."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import numpy as np
import pandas as pd

from app.services.prescoring_explanations import compute_shap_values_dict


@patch("app.services.prescoring_explanations.shap_available", return_value=True)
@patch("app.services.prescoring_explanations._build_tree_explainer")
@patch("app.services.prescoring_explanations._unwrap_lightgbm_estimator")
def test_compute_shap_via_tree_explainer(mock_unwrap, mock_build_explainer, _mock_shap_ok):
    mock_unwrap.return_value = object()
    explainer = MagicMock()
    explainer.shap_values.return_value = np.array([[0.12, -0.05, 0.03]])
    mock_build_explainer.return_value = explainer

    X = pd.DataFrame({"a": [1.0], "b": [2.0], "c": [3.0]})
    out = compute_shap_values_dict(
        model=MagicMock(),
        X=X,
        feature_names=["a", "b", "c"],
        bundle_explainer=None,
    )

    assert out == {"a": 0.12, "b": -0.05, "c": 0.03}
    explainer.shap_values.assert_called_once()
