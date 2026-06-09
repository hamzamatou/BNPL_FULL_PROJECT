from datetime import date

from app.services.ollama_service import (
    _anciennete_mois_from_attestation_text,
    _extract_date_embauche_from_ocr,
    _finalize_attestation_anciennete,
    _months_between_dates,
    _parse_date_embauche,
)


def test_months_between_dates_full_months():
    start = date(2020, 1, 15)
    end = date(2024, 6, 14)
    assert _months_between_dates(start, end) == 52
    assert _months_between_dates(end, start) == 0


def test_parse_date_embauche_formats():
    assert _parse_date_embauche("2019-03-15") == date(2019, 3, 15)
    assert _parse_date_embauche("15/03/2019") == date(2019, 3, 15)


def test_extract_date_embauche_depuis_le():
    ocr = "Attestation de travail\nEmploye: Dupont\nEn service depuis le 01/06/2020"
    assert _extract_date_embauche_from_ocr(ocr) == date(2020, 6, 1)


def test_anciennete_from_date_embauche_reference():
    ocr = "Date d'embauche: 01/01/2022"
    ref = date(2024, 7, 1)
    assert _anciennete_mois_from_attestation_text(ocr, reference_date=ref) == 30


def test_anciennete_explicit_ans():
    ocr = "Anciennete dans l'emploi: 3 ans"
    assert _anciennete_mois_from_attestation_text(ocr, reference_date=date.today()) == 36


def test_finalize_from_llm_date_embauche():
    parsed = {"date_embauche": "2019-03-15", "valeur": None}
    ref = date(2024, 3, 15)
    assert _finalize_attestation_anciennete(parsed, None, "", reference_date=ref) == "60"


def test_finalize_from_ocr_when_llm_empty():
    ocr = "Depuis le 15/03/2019"
    ref = date(2024, 3, 15)
    assert _finalize_attestation_anciennete({}, None, ocr, reference_date=ref) == "60"
