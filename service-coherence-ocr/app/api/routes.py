import json
from flask import Blueprint, jsonify, request

from app.services.coherence_service import verifier_coherence_dossier
from app.services.ocr_service import OCRDocumentError
from app.services.service_recommendation import (
    DossierFinancier,
    generer_recommandations
)

api_bp = Blueprint("api", __name__)


# =========================
# HEALTH CHECK
# =========================
@api_bp.get("/health")
def health():
    return jsonify({"status": "UP"}), 200


# =========================
# COHERENCE CHECK
# =========================
@api_bp.post("/coherence/check")
def coherence_check():

    # 1. récupérer les données déclarées
    declared_json = request.form.get("declared_data", "")

    if not declared_json:
        return jsonify({
            "message": "declared_data est obligatoire"
        }), 400

    # 2. parser JSON
    try:
        declared_data = json.loads(declared_json)
    except Exception:
        return jsonify({
            "message": "declared_data invalide"
        }), 400

    # 3. récupérer fichiers uploadés
    files = request.files

    try:
        # 4. appel service principal
        result = verifier_coherence_dossier(
            donnees_declarees=declared_data,
            fichiers=files
        )

        return jsonify(result), 200

    except OCRDocumentError as ex:
        return jsonify({
            "message": "Erreur OCR sur document",
            "detail": str(ex)
        }), 400

    except Exception as ex:
        return jsonify({
            "message": "Erreur technique interne",
            "detail": str(ex)
        }), 500
@api_bp.get("/recommendation/generate")
def generate_recommendation():

    try:
        # ─────────────────────────────
        # 1. Récupération des paramètres depuis query params
        # ─────────────────────────────
        dossier = DossierFinancier(
            revenu_mensuel_net=float(request.args.get("revenu_mensuel_net", 0)),
            charges_mensuelles_totales=float(request.args.get("charges_mensuelles_totales", 0)),
            mensualites_credits_existants=float(request.args.get("mensualites_credits_existants", 0)),
            encours_credits=float(request.args.get("encours_credits", 0)),
            anciennete_emploi_mois=int(request.args.get("anciennete_emploi_mois", 0)),
            montant_financement=float(request.args.get("montant_financement", 0)),
            duree_mois=int(request.args.get("duree_mois", 0)),
        )

    except Exception as ex:
        return jsonify({
            "message": "Paramètres invalides",
            "detail": str(ex)
        }), 400

    try:
        # ─────────────────────────────
        # 2. Appel service recommandation
        # ─────────────────────────────
        result = generer_recommandations(dossier)

        return jsonify(result.to_dict()), 200

    except Exception as ex:
        return jsonify({
            "message": "Erreur génération recommandation",
            "detail": str(ex)
        }), 500