"""Tests unitaires prescoring (sans charger le modele .pkl)."""
from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import pytest

from app.services import prescoring_service
from app.services.prescoring_explanations import generate_leviers_score
from app.services.prescoring_service import (
    _explications_texte_analyste,
    _parse_row,
    _zone_selon_pd,
    get_prescoring_status,
)


def _valid_body() -> dict:
    return {
        "revenu_mensuel_net": "2200",
        "revenu_annuel": "26400",
        "charges_mensuelles_totales": "750",
        "montant_demande": "3000",
        "nbr_mois_remboursement": "12",
        "anciennete_emploi_mois": "48",
        "type_contrat": "CDI",
    }


def test_parse_row_ok():
    row = _parse_row(_valid_body())
    assert row["revenu_mensuel_net"] == 2200.0
    assert row["type_contrat"] == "CDI"
    assert row["montant_demande"] == 3000.0


def test_parse_row_champs_manquants():
    with pytest.raises(ValueError, match="Champs manquants"):
        _parse_row({"revenu_mensuel_net": "2200"})


def test_parse_row_montant_invalide():
    body = _valid_body()
    body["montant_demande"] = "0"
    with pytest.raises(ValueError, match="montant_demande"):
        _parse_row(body)


def test_parse_row_valeur_non_numerique():
    body = _valid_body()
    body["revenu_annuel"] = "abc"
    with pytest.raises(ValueError, match="numerique invalide"):
        _parse_row(body)


@pytest.mark.parametrize(
    "pd,expected_code",
    [
        (0.10, "vert"),
        (0.30, "vert"),
        (0.31, "orange"),
        (0.60, "orange"),
        (0.61, "rouge"),
    ],
)
def test_zone_selon_pd(pd, expected_code):
    zone = _zone_selon_pd(pd)
    assert zone["code"] == expected_code


def test_generate_leviers_score_hausse_baisse():
    shap_vals = {
        "mensualite_bnpl": 0.15,
        "buffer_financier": -0.08,
        "stress_financier": 0.05,
        "nbr_mois_remboursement": 0.001,
    }
    text = generate_leviers_score(shap_vals)
    assert text is not None
    assert "tire vers le haut" in text
    assert "limite par" in text
    assert "Mensualité BNPL" in text
    assert "Marge financière" in text or "buffer" in text.lower()
    assert "0.15" not in text
    assert "SHAP" not in text


def test_generate_leviers_score_vide():
    assert generate_leviers_score({}) == "Leviers du score : aucun facteur majeur identifie sur ce dossier."
    assert generate_leviers_score(None) is None


def test_explications_texte_analyste_structure():
    foret = {"atypique": False, "score_echantillon": -0.4, "predict_sklearn": 1}
    leviers = (
        "Le score est tire vers le haut par la Mensualité BNPL ; "
        "il est limite par le Reste à vivre."
    )
    lines = _explications_texte_analyste(foret, leviers)
    assert len(lines) == 3
    assert "typique" in lines[0].lower()
    assert lines[1] == leviers
    assert "Indicateur statistique" in lines[2]
    assert "918" not in " ".join(lines)
    assert "Repere score" not in " ".join(lines)


def test_explications_sans_foret_ni_shap():
    lines = _explications_texte_analyste(None, None, shap_lib_ok=False)
    assert len(lines) == 3
    assert "non presente" in lines[0].lower()
    assert "non disponibles" in lines[1].lower()


def test_get_prescoring_status_modele_absent(tmp_path, monkeypatch):
    missing = tmp_path / "modele_inexistant.pkl"
    monkeypatch.setattr(prescoring_service.Settings, "BNPL_MODEL_PATH", str(missing))
    status = get_prescoring_status()
    assert status["ready"] is False
    assert "introuvable" in status["error"].lower() or "Fichier" in status["error"]


def test_get_prescoring_status_modele_present(tmp_path, monkeypatch):
    model_file = tmp_path / "bnpl_model_production.pkl"
    model_file.write_bytes(b"fake")
    monkeypatch.setattr(prescoring_service.Settings, "BNPL_MODEL_PATH", str(model_file))
    status = get_prescoring_status()
    assert status["ready"] is True
    assert status["error"] is None


@patch("app.api.routes.prescore_dossier")
def test_route_prescore_ok(mock_prescore, client):
    mock_prescore.return_value = {"score": 720, "pd_pct": 12.5, "zone": {"code": "vert"}}
    response = client.get(
        "/prescoring/prescore",
        query_string=_valid_body(),
    )
    assert response.status_code == 200
    assert response.get_json()["score"] == 720


def test_route_prescore_parametres_manquants(client):
    response = client.get("/prescoring/prescore", query_string={"revenu_mensuel_net": "2200"})
    assert response.status_code == 400
    assert "invalide" in response.get_json()["message"].lower()
