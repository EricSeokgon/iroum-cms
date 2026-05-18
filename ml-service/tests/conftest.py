"""pytest 공용 픽스처 (SPEC-CMS-ML-SERVICE-001)."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="session")
def client() -> TestClient:
    """FastAPI 테스트 클라이언트 (세션 스코프 — 임베더 1회 로드)."""
    return TestClient(app)
