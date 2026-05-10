"""
Extraction legere du CIN apres etiquette type « CIN: » sur le texte OCR brut.
Ne remplace pas l'extraction LLM ; sert aux controles croises entre pieces.
"""

import re
from typing import Dict, Optional

_EASTERN_ARABIC_DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")


def _digits_only_eastern_normalized(s: str) -> str:
    t = (s or "").translate(_EASTERN_ARABIC_DIGITS)
    return "".join(ch for ch in t if ch.isdigit())


def extract_cin_after_label(ocr_text: str) -> Optional[str]:
    """
    Cherche un motif du type CIN : 12345678 (8 chiffres) sur le texte OCR.
    Retourne 8 chiffres latins ou None.
    """
    t = (ocr_text or "").translate(_EASTERN_ARABIC_DIGITS)
    # Accepte separateurs OCR dans les chiffres: espaces, points, tirets.
    # Ex: "CIN : 14 529 835" -> 14529835
    patterns = [
        r"(?i)\bCIN\b\s*[:#]?\s*([0-9][0-9\s.\-]{7,20})\b",
        r"(?i)\bC\.?\s*I\.?\s*N\.?\b\s*[:#]?\s*([0-9][0-9\s.\-]{7,20})\b",
         r"بطاقة التعريف الوطنية\s*[:#]?\s*([0-9٠-٩][0-9٠-٩\s.\-]{7,20})",
    # Arabe — abréviation ب.ت.و
    r"ب\.?\s*ت\.?\s*و\.?\s*[:#]?\s*([0-9٠-٩][0-9٠-٩\s.\-]{7,20})",
    ]
    for pat in patterns:
        m = re.search(pat, t)
        if not m:
            continue
        digits = _digits_only_eastern_normalized(m.group(1))
        if len(digits) == 8:
            return digits
        # Cas frequent OCR: groupe avec bruit mais debut correct.
        if len(digits) > 8:
            head = digits[:8]
            if len(head) == 8:
                return head
    return None


def has_cin_label(ocr_text: str) -> bool:
    """Detecte seulement la presence du libelle CIN, meme si les chiffres sont illisibles."""
    t = (ocr_text or "").translate(_EASTERN_ARABIC_DIGITS)
    return re.search(r"(?i)\bC\.?\s*I\.?\s*N\.?\b", t) is not None


def extract_cin_labeled_per_document(ocr_by_doc: Dict[str, str]) -> Dict[str, Optional[str]]:
    """Pour chaque type de document traite, CIN apres etiquette ou None."""
    return {doc_type: extract_cin_after_label(text) for doc_type, text in (ocr_by_doc or {}).items()}
