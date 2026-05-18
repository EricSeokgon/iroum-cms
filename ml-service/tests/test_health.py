"""GET /ml/v1/health 명세 테스트."""

from __future__ import annotations

EXPECTED_MODELS = {
    "growth-stage-rule-v1",
    "risk-score-rule-v1",
    "simulation-rule-v1",
    "policy-match-embed-v1",
    "sentence-transformers-384d",
    "rag-template-v1",
}


def test_health_status_up(client):
    """status 가 UP 이다."""
    r = client.get("/ml/v1/health")
    assert r.status_code == 200
    assert r.json()["status"] == "UP"


def test_health_camelcase_fields(client):
    """응답 필드명이 Java MlHealthResponse 와 일치한다."""
    body = client.get("/ml/v1/health").json()
    assert set(body.keys()) == {"status", "loadedModels"}


def test_loaded_models_list(client):
    """loadedModels 가 6개 모델 식별자를 모두 포함한다."""
    models = client.get("/ml/v1/health").json()["loadedModels"]
    assert isinstance(models, list)
    assert set(models) == EXPECTED_MODELS
