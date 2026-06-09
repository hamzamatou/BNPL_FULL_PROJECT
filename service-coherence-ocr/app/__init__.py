from dotenv import load_dotenv

# Charger .env avant routes / ocr_service (TESSERACT_CMD lu a l'import).
load_dotenv()

from flask import Flask

from app.api.routes import api_bp


def create_app() -> Flask:
    app = Flask(__name__)
    app.register_blueprint(api_bp)
    return app
