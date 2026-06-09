from flask import Blueprint, jsonify, request

from app.models import DossierScoringInput
from app.criteria import BANQUE_EL_AMEN, BANQUE_EL_BARAKA
from app.scoring_formulas import calculer_score_banque, reponse_api_banque
from app.routage import evaluer_routage

api_bp = Blueprint("api", __name__)


def _parse_dossier_from_query() -> DossierScoringInput:
    def f(name: str, default: float | None = None, required: bool = False) -> float:
        raw = request.args.get(name)
        if raw is None or raw == "":
            if required:
                raise ValueError(f"Parametre obligatoire: {name}")
            if default is None:
                raise ValueError(f"Parametre obligatoire: {name}")
            return default
        return float(raw)

    def i(name: str, default: int | None = None, required: bool = False) -> int:
        raw = request.args.get(name)
        if raw is None or raw == "":
            if required:
                raise ValueError(f"Parametre obligatoire: {name}")
            if default is None:
                raise ValueError(f"Parametre obligatoire: {name}")
            return default
        return int(float(raw))

    return DossierScoringInput(
        revenu_mensuel_net=f("revenu_mensuel_net", required=True),
        charges_mensuelles_totales=f("charges_mensuelles_totales", required=True),
        montant_demande=f("montant_demande", required=True),
        duree_mois=i("duree_mois", required=True),
        anciennete_emploi_mois=i("anciennete_emploi_mois", default=0),
        type_contrat=request.args.get("type_contrat", "CDI"),
        nb_incidents_paiement=i("nb_incidents_paiement", default=0),
        score_centrale_risque=f("score_centrale_risque", default=50.0),
    )


def _parse_dossier_from_json() -> DossierScoringInput:
    data = request.get_json(silent=True) or {}
    return DossierScoringInput(
        revenu_mensuel_net=float(data["revenu_mensuel_net"]),
        charges_mensuelles_totales=float(data["charges_mensuelles_totales"]),
        montant_demande=float(data["montant_demande"]),
        duree_mois=int(data["duree_mois"]),
        anciennete_emploi_mois=int(data.get("anciennete_emploi_mois", 0)),
        type_contrat=str(data.get("type_contrat", "CDI")),
        nb_incidents_paiement=int(data.get("nb_incidents_paiement", 0)),
        score_centrale_risque=float(data.get("score_centrale_risque", 50)),
    )


@api_bp.get("/health")
def health():
    return jsonify({"status": "UP", "service": "scoring-banques-interne"}), 200


@api_bp.get("/banques/el-amen/score-interne")
def score_el_amen():
    """API simulée El Amen : accepte ou non (détails de scoring non exposés)."""
    try:
        dossier = _parse_dossier_from_query()
        return jsonify(reponse_api_banque(calculer_score_banque(BANQUE_EL_AMEN, dossier))), 200
    except (ValueError, TypeError) as ex:
        return jsonify({"message": "Requete invalide", "detail": str(ex)}), 400


@api_bp.get("/banques/el-baraka/score-interne")
def score_el_baraka():
    """API simulée El Baraka : accepte ou non (détails de scoring non exposés)."""
    try:
        dossier = _parse_dossier_from_query()
        return jsonify(reponse_api_banque(calculer_score_banque(BANQUE_EL_BARAKA, dossier))), 200
    except (ValueError, TypeError) as ex:
        return jsonify({"message": "Requete invalide", "detail": str(ex)}), 400


@api_bp.post("/routage/evaluer")
def routage_evaluer():
    """Routage : UIB par défaut + partenaires acceptés. Réponse = banquesRoutees uniquement."""
    try:
        dossier = _parse_dossier_from_json() if request.is_json else _parse_dossier_from_query()
        return jsonify(evaluer_routage(dossier)), 200
    except (ValueError, TypeError, KeyError) as ex:
        return jsonify({"message": "Requete invalide", "detail": str(ex)}), 400


@api_bp.get("/routage/evaluer")
def routage_evaluer_get():
    try:
        dossier = _parse_dossier_from_query()
        return jsonify(evaluer_routage(dossier)), 200
    except (ValueError, TypeError) as ex:
        return jsonify({"message": "Requete invalide", "detail": str(ex)}), 400
