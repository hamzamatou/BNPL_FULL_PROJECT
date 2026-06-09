from app.models import DossierScoringInput
from app.criteria import BANQUE_EL_AMEN, BANQUE_EL_BARAKA, BANQUE_UIB
from app.scoring_formulas import calculer_score_banque, reponse_api_banque
from app.routage import evaluer_routage


def _dossier_favorable() -> DossierScoringInput:
    return DossierScoringInput(
        revenu_mensuel_net=2800,
        charges_mensuelles_totales=650,
        montant_demande=6000,
        duree_mois=12,
        anciennete_emploi_mois=48,
        type_contrat="CDI",
        nb_incidents_paiement=0,
        score_centrale_risque=78,
    )


def _dossier_risque() -> DossierScoringInput:
    return DossierScoringInput(
        revenu_mensuel_net=1200,
        charges_mensuelles_totales=520,
        montant_demande=8000,
        duree_mois=12,
        anciennete_emploi_mois=4,
        type_contrat="INTERIM",
        nb_incidents_paiement=3,
        score_centrale_risque=32,
    )


def test_api_banque_ne_expose_que_accepte():
    d = _dossier_favorable()
    rep = reponse_api_banque(calculer_score_banque(BANQUE_EL_AMEN, d))
    assert rep == {"accepte": True}
    assert "scoreInterne" not in rep
    assert "criteres" not in rep


def test_routage_reponse_minimale():
    out = evaluer_routage(_dossier_favorable())
    assert list(out.keys()) == ["banquesRoutees"]
    assert out["banquesRoutees"][0] == BANQUE_UIB
    assert BANQUE_EL_AMEN in out["banquesRoutees"]
    assert BANQUE_EL_BARAKA in out["banquesRoutees"]


def test_routage_uib_seul_si_aucun_partenaire():
    out = evaluer_routage(_dossier_risque())
    assert out["banquesRoutees"][0] == BANQUE_UIB
    assert BANQUE_EL_AMEN not in out["banquesRoutees"] or BANQUE_EL_BARAKA not in out["banquesRoutees"]


def test_formules_differentes_entre_banques_en_interne():
    d = _dossier_favorable()
    amen = calculer_score_banque(BANQUE_EL_AMEN, d)
    baraka = calculer_score_banque(BANQUE_EL_BARAKA, d)
    assert amen.score_interne != baraka.score_interne or amen.criteres != baraka.criteres
