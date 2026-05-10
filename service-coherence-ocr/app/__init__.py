from flask import Flask
from dotenv import load_dotenv

from app.api.routes import api_bp


def create_app() -> Flask:
    load_dotenv()
    app = Flask(__name__)
    app.register_blueprint(api_bp)
    return app
