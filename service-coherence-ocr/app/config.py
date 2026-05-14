import os
from pathlib import Path


def _env_path(name: str, default: str = "") -> str:
    """Lit une variable d'environnement ; chemins Windows sans ambiguite \\t (tab)."""
    v = os.getenv(name, default) or ""
    v = v.strip().strip('"').strip("'")
    return v


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or str(raw).strip() == "":
        return default
    try:
        return int(str(raw).strip())
    except ValueError:
        return default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None or str(raw).strip() == "":
        return default
    return str(raw).strip().lower() in {"1", "true", "yes", "y", "on"}


# uib-bnpl/bnpl-data-pipeline (defaut : frere de service-coherence-ocr)
_REPO_ROOT = Path(__file__).resolve().parent.parent.parent
_DEFAULT_BNPL_PIPELINE = str(_REPO_ROOT / "bnpl-data-pipeline")


class Settings:
    OLLAMA_URL = os.getenv("OLLAMA_URL", "http://localhost:11434/api/generate")
    # Defaut leger pour latence raisonnable sans GPU ; surchargez avec llama3.1:8b si besoin de precision.
    OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2:3b")
    OLLAMA_TIMEOUT_SEC = _env_int("OLLAMA_TIMEOUT_SEC", 90)
    OLLAMA_MAX_OCR_CHARS = _env_int("OLLAMA_MAX_OCR_CHARS", 5000)
    # Limite les tokens generes (JSON court) -> generations plus courtes.
    OLLAMA_NUM_PREDICT = _env_int("OLLAMA_NUM_PREDICT", 512)
    # Appel LLM unique par dossier (tous les documents) : JSON plus long, timeout et plafond tokens dedies.
    COHERENCE_LLM_BATCH = _env_bool("COHERENCE_LLM_BATCH", True)
    OLLAMA_TIMEOUT_BATCH_SEC = _env_int("OLLAMA_TIMEOUT_BATCH_SEC", 0)
    OLLAMA_NUM_PREDICT_BATCH = _env_int("OLLAMA_NUM_PREDICT_BATCH", 2048)
    OCR_LANG = os.getenv("OCR_LANG", "fra+ara+eng")
    TESSERACT_CMD = _env_path("TESSERACT_CMD", "")
    # OCR en parallele sur les documents d'une meme requete (Tesseract libere le GIL peu : threads utiles surtout multi-pages / I/O).
    COHERENCE_OCR_WORKERS = max(1, _env_int("COHERENCE_OCR_WORKERS", 4))
    # Si le texte OCR contient deja 8 chiffres CIN clairs, pas d'appel Ollama pour ce document.
    COHERENCE_SKIP_LLM_FOR_CIN = _env_bool("COHERENCE_SKIP_LLM_FOR_CIN", True)
    # Si true: regex avant LLM (plus rapide). Defaut true. Mettre false pour prioriser le LLM puis regex en secours.
    COHERENCE_REGEX_FIRST = _env_bool("COHERENCE_REGEX_FIRST", True)
    # Redimensionne avant Tesseract si le plus grand cote depasse (0 = desactive). Gain sur gros scans.
    OCR_IMAGE_MAX_EDGE = _env_int("OCR_IMAGE_MAX_EDGE", 2200)

    # --- Prescoring (LightGBM + IF + SHAP, bundle train_GBMlight) ---
    BNPL_PIPELINE_DIR = _env_path("BNPL_PIPELINE_DIR", _DEFAULT_BNPL_PIPELINE)
    _BNPL_MODEL_ENV = _env_path("BNPL_MODEL_PATH", "")
    BNPL_MODEL_PATH = _BNPL_MODEL_ENV or str(Path(BNPL_PIPELINE_DIR) / "bnpl_model_production.pkl")
