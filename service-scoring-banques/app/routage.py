"""Agrégation interne : UIB par défaut + partenaires éligibles (scores non exposés)."""

from app.criteria import BANQUE_EL_AMEN, BANQUE_EL_BARAKA, BANQUE_UIB
from app.models import DossierScoringInput
from app.scoring_formulas import calculer_score_banque

BANQUES_PARTENAIRES = (BANQUE_EL_AMEN, BANQUE_EL_BARAKA)


def evaluer_routage(dossier: DossierScoringInput) -> dict:
    """
    Réponse publique minimale : liste des banques routées uniquement.
    UIB est toujours en tête ; les partenaires sont ajoutés si leur scoring interne accepte le dossier.
    """
    partenaires_eligibles = [
        code
        for code in BANQUES_PARTENAIRES
        if calculer_score_banque(code, dossier).eligible
    ]
    return {"banquesRoutees": [BANQUE_UIB, *partenaires_eligibles]}
