"""Fixtures pytest pour service-coherence-ocr."""
from __future__ import annotations

import pytest

from app import create_app
from app.services import prescoring_service


@pytest.fixture
def app():
    return create_app()


@pytest.fixture
def client(app):
    return app.test_client()


@pytest.fixture(autouse=True)
def reset_prescoring_cache():
    """Evite la pollution du cache modele entre tests."""
    prescoring_service._meta = None
    prescoring_service._meta_load_error = None
    yield
    prescoring_service._meta = None
    prescoring_service._meta_load_error = None
