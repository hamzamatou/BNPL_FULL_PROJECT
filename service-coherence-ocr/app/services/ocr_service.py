import io
import re

import cv2
import fitz
import numpy as np
import pytesseract
from PIL import Image

from app.config import Settings


if Settings.TESSERACT_CMD:
    pytesseract.pytesseract.tesseract_cmd = Settings.TESSERACT_CMD


class OCRDocumentError(Exception):
    """Base error for OCR document validation/decoding."""


class UnsupportedDocumentTypeError(OCRDocumentError):
    """Raised when uploaded bytes are neither image nor PDF."""


class MultiPagePdfNotSupportedError(OCRDocumentError):
    """Raised when uploaded PDF has more than one page."""


def _maybe_downscale_rgb(image: Image.Image) -> Image.Image:
    max_edge = int(Settings.OCR_IMAGE_MAX_EDGE or 0)
    if max_edge <= 0:
        return image
    w, h = image.size
    m = max(w, h)
    if m <= max_edge:
        return image
    scale = max_edge / float(m)
    nw = max(1, int(w * scale))
    nh = max(1, int(h * scale))
    return image.resize((nw, nh), Image.Resampling.LANCZOS)


def _extract_text_from_rgb_image(image: Image.Image) -> str:
    image = _maybe_downscale_rgb(image)
    np_img = np.array(image)
    gray = cv2.cvtColor(np_img, cv2.COLOR_RGB2GRAY)
    gray = cv2.GaussianBlur(gray, (3, 3), 0)
    th = cv2.adaptiveThreshold(
        gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 31, 11
    )
    text = pytesseract.image_to_string(th, lang="fra+ara+eng")
    return (text or "").strip()


def _clean_text(text: str) -> str:
    t = text or ""
    t = re.sub(r"\n+", "\n", t)
    t = re.sub(r"[ \t]+", " ", t)
    return t.strip()


def _text_score(text: str) -> int:
    """Simple quality score to pick best PDF read."""
    t = (text or "").strip().lower()
    if not t:
        return 0
    score = 0
    score += len(re.findall(r"[a-zà-ÿ]", t))
    score += 3 * len(re.findall(r"\d", t))
    for kw in ("cin", "net", "payer", "loyer", "montant", "total", "salaire"):
        if kw in t:
            score += 80
    return score


def _extract_text_from_pdf_one_page(file_bytes: bytes) -> str:
    try:
        doc = fitz.open(stream=file_bytes, filetype="pdf")
    except Exception as ex:
        raise UnsupportedDocumentTypeError("PDF invalide ou corrompu.") from ex

    try:
        if doc.page_count != 1:
            raise MultiPagePdfNotSupportedError(
                f"PDF multi-pages non supporte (pages={doc.page_count}). Fournissez un PDF 1 page."
            )
        page = doc.load_page(0)

        # 1) Lecture texte natif PDF (quand disponible)
        native_text = _clean_text(page.get_text() or "")

        # 2) OCR raster sur plusieurs DPI, puis garder le meilleur
        ocr_candidates: list[str] = []
        for dpi in (300, 400):
            pix = page.get_pixmap(alpha=False, dpi=dpi)
            image = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
            ocr_candidates.append(_clean_text(_extract_text_from_rgb_image(image)))
        best_ocr = max(ocr_candidates, key=_text_score, default="")

        # 3) Fusion: si natif est faible, prendre OCR; sinon garder le meilleur
        if _text_score(native_text) < 120:
            return best_ocr
        return max([native_text, best_ocr], key=_text_score)
    finally:
        doc.close()


def extract_text_from_document(file_bytes: bytes) -> str:
    """Extract OCR text from image or single-page PDF bytes."""
    if not file_bytes:
        raise UnsupportedDocumentTypeError("Fichier vide.")

    # PDF header: %PDF-
    if file_bytes[:5] == b"%PDF-":
        return _extract_text_from_pdf_one_page(file_bytes)

    try:
        image = Image.open(io.BytesIO(file_bytes)).convert("RGB")
    except Exception as ex:
        raise UnsupportedDocumentTypeError(
            "Type de fichier non supporte. Utilisez une image (JPG/PNG) ou un PDF 1 page."
        ) from ex
    return _extract_text_from_rgb_image(image)
