"""Tests règles de cohérence métier (revenu, montants identiques)."""
from app.services.coherence_service import (
    _montants_identiques,
    _revenu_coherent,
    _revenu_coherent_result,
    verifier_coherence_metier,
)


def test_revenu_acceptable_dans_tolerance_10_pourcent():
    assert _revenu_coherent(2500, 2500) is None
    assert _revenu_coherent(2500, 2250) is None
    assert _revenu_coherent(2500, 2400) is None


def test_revenu_refuse_si_superieur_au_declare():
    msg = _revenu_coherent(2500, 2600)
    assert msg is not None
    assert "supérieur" in msg


def test_revenu_refuse_si_inferieur_de_plus_10_pourcent():
    msg = _revenu_coherent(2500, 2249)
    assert msg is not None
    assert "10 %" in msg


def test_revenu_declare_trop_haut_fourchette_depuis_extrait():
    """Ex. extrait 2500, déclaré 4000 → fourchette 2250–2778 (pas 3600)."""
    result = _revenu_coherent_result(4000, 2500)
    assert result is not None
    details = result["details"]
    assert details["fourchette_declarable_min"] == 2250
    assert details["fourchette_declarable_max"] == 2777.78
    assert details["revenu_document_minimum"] == 3600
    assert "2 250" in result["message"] or "2250" in result["message"]
    assert "3600" not in result["message"]


def test_montants_identiques_arrondis_deux_decimales():
    assert _montants_identiques(500.0, 500.001) is True
    assert _montants_identiques(500.0, 501.0) is False


def test_verifier_coherence_metier_revenu_ok():
    result = verifier_coherence_metier(
        {"revenu_mensuel": 2500},
        {"revenu_mensuel": 2250},
        None,
    )
    assert result["anomalies"] == []
    assert "score_coherence" not in result


def test_verifier_coherence_metier_revenu_bloquant_si_trop_eleve():
    result = verifier_coherence_metier(
        {"revenu_mensuel": 2500},
        {"revenu_mensuel": 2600},
        None,
    )
    assert len(result["anomalies"]) == 1
    assert result["anomalies"][0]["code"] == "COH_REVENU_DIFF"
    assert result["anomalies"][0]["niveau"] == "BLOQUANT"


def test_verifier_coherence_metier_loyer_doit_etre_identique():
    result = verifier_coherence_metier(
        {"loyer_mensuel": 600},
        {"loyer_mensuel": 550},
        None,
    )
    assert len(result["anomalies"]) == 1
    assert result["anomalies"][0]["code"] == "COH_LOYER_DIFF"
    assert result["anomalies"][0]["niveau"] == "BLOQUANT"


def test_verifier_coherence_metier_montant_devis_identique():
    result = verifier_coherence_metier(
        {"montant": 12000},
        {"montant_devis": 12000},
        None,
    )
    assert result["anomalies"] == []

    result_ko = verifier_coherence_metier(
        {"montant": 12000},
        {"montant_devis": 11500},
        None,
    )
    assert result_ko["anomalies"][0]["code"] == "COH_DEVIS_DIFF"
