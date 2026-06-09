"""Tests filtrage et format de réponse des recommandations."""
from app.services.service_recommendation import (
    RecommandationResult,
    _filtrer_recommandations_financieres,
)


def test_filtrer_exclut_conseils_documents():
    entree = [
        "Réduire le montant à 2 500 TND sur 24 mois.",
        "Joindre une fiche de paie supplémentaire.",
        "Allonger la durée à 36 mois.",
    ]
    sortie = _filtrer_recommandations_financieres(entree)
    assert len(sortie) == 2
    assert "montant" in sortie[0].lower()
    assert "36 mois" in sortie[1]


def test_to_dict_ne_contient_que_recommandations():
    result = RecommandationResult(
        conforme=True,
        mensualite_bnpl=100.0,
        revenu_disponible=2000.0,
        plafond_bnpl=500.0,
        montant_max_acceptable=None,
        duree_minimale_mois=None,
        score_solvabilite="Bon",
        evaluation="ok",
        recommandations=["Réduire le montant à 2 000 TND."],
        texte_complet="",
        raw_llm_response=None,
    )
    payload = result.to_dict()
    assert payload == {"recommandations": ["Réduire le montant à 2 000 TND."]}
    assert "score_coherence" not in payload
    assert "score_solvabilite" not in payload
