"""Tests unitaires validation dossier (coherence + recommandations)."""
from __future__ import annotations

from unittest.mock import patch

from app.services.dossier_validation_service import (
    _build_dossier_financier,
    valider_dossier_et_recommander,
)
from app.services.service_recommendation import RecommandationResult


def test_build_dossier_financier_calcule_charges_enfants():
    declared = {
        "revenu_mensuel_net": 2500,
        "loyer_mensuel": 500,
        "mensualites_credits": 200,
        "autres_charges_fixes": 100,
        "nombre_enfants": 2,
        "montant": 4000,
        "duree_mois": 12,
    }
    dossier = _build_dossier_financier(declared)
    assert dossier.revenu_mensuel_net == 2500.0
    assert dossier.charges_mensuelles_totales == 500 + 200 + 100 + 600
    assert dossier.montant_financement == 4000.0


@patch("app.services.dossier_validation_service.generer_recommandations")
@patch("app.services.dossier_validation_service.verifier_coherence_dossier")
def test_valider_dossier_anomalies_non_vides_sans_recommandations(
    mock_coherence, mock_reco
):
    mock_coherence.return_value = {
        "anomalies": [{"code": "CIN", "message": "CIN incohérent"}],
        "corrections": {"cin": "12345678"},
    }

    result = valider_dossier_et_recommander({"montant": 3000}, {})

    assert result["recommandations"] == []
    assert len(result["anomalies"]) == 1
    mock_reco.assert_not_called()


@patch("app.services.dossier_validation_service.generer_recommandations")
@patch("app.services.dossier_validation_service.verifier_coherence_dossier")
def test_valider_dossier_sans_anomalies_avec_recommandations(mock_coherence, mock_reco):
    mock_coherence.return_value = {
        "anomalies": [],
        "corrections": {},
    }
    mock_reco.return_value = RecommandationResult(
        conforme=True,
        mensualite_bnpl=250.0,
        revenu_disponible=1800.0,
        plafond_bnpl=500.0,
        montant_max_acceptable=None,
        duree_minimale_mois=None,
        score_solvabilite="BON",
        evaluation="Dossier conforme",
        recommandations=["Envisager 24 mois pour reduire la mensualite."],
        texte_complet="Dossier conforme",
        raw_llm_response=None,
    )

    result = valider_dossier_et_recommander(
        {"montant": 3000, "revenu_mensuel_net": 2200, "duree_mois": 12},
        {},
    )

    assert result["anomalies"] == []
    assert len(result["recommandations"]) == 1
    mock_reco.assert_called_once()


@patch("app.services.dossier_validation_service.verifier_coherence_dossier")
def test_valider_dossier_documents_manquants(mock_coherence):
    mock_coherence.return_value = {
        "documents_manquants": ["cin", "fiche_paie_m1"],
        "anomalies": [],
        "corrections": {},
    }

    result = valider_dossier_et_recommander({}, {})

    assert result["recommandations"] == []
    assert result["anomalies"][0]["code"] == "DOCS_MANQUANTS"
    assert "score_coherence" not in result
