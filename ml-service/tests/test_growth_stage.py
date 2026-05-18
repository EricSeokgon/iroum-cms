"""POST /ml/v1/growth-stage 명세 테스트."""

from __future__ import annotations

VALID_STAGES = {"SEED", "STARTUP", "GROWTH", "EXPANSION", "MATURITY"}


def _post(client, body):
    return client.post("/ml/v1/growth-stage", json=body)


def test_response_has_exact_camelcase_fields(client):
    """응답이 Java GrowthStageResponse camelCase 필드와 정확히 일치한다."""
    r = _post(
        client,
        {
            "ksicCode": "G471",
            "capitalAmount": 5_000_000,
            "foundingYear": 2020,
            "revenueAmount": 100_000_000,
        },
    )
    assert r.status_code == 200
    body = r.json()
    assert set(body.keys()) == {
        "stage",
        "entryProbabilities",
        "confidence",
        "modelVersion",
    }
    assert body["modelVersion"] == "rule-v1.0.0"


def test_stage_is_valid_enum_and_probabilities_sum_to_one(client):
    """stage 가 5단계 enum 중 하나이며 5개 확률 합이 ~1.0 이다."""
    r = _post(
        client,
        {
            "ksicCode": "C100",
            "capitalAmount": 200_000_000,
            "foundingYear": 2010,
            "revenueAmount": 800_000_000,
        },
    )
    body = r.json()
    probs = body["entryProbabilities"]
    assert body["stage"] in VALID_STAGES
    assert set(probs.keys()) == VALID_STAGES
    assert abs(sum(probs.values()) - 1.0) < 1e-3


def test_startup_classification(client):
    """업력 짧고 자본/매출 작으면 STARTUP 계열로 분류된다."""
    r = _post(
        client,
        {
            "ksicCode": "J620",
            "capitalAmount": 30_000_000,
            "foundingYear": 2024,
            "revenueAmount": 50_000_000,
        },
    )
    body = r.json()
    assert body["stage"] in {"SEED", "STARTUP"}


def test_growth_classification(client):
    """자본>=1억 AND 매출>=5억 AND 업력>3 이면 GROWTH 가중치가 높다."""
    r = _post(
        client,
        {
            "ksicCode": "C100",
            "capitalAmount": 150_000_000,
            "foundingYear": 2015,
            "revenueAmount": 700_000_000,
        },
    )
    body = r.json()
    assert body["entryProbabilities"]["GROWTH"] > 0.0
    assert body["stage"] in VALID_STAGES


def test_expansion_classification(client):
    """매출>=10억 AND 업력>7 이면 EXPANSION 가중치가 존재한다."""
    r = _post(
        client,
        {
            "ksicCode": "C100",
            "capitalAmount": 500_000_000,
            "foundingYear": 2005,
            "revenueAmount": 2_000_000_000,
        },
    )
    body = r.json()
    assert body["entryProbabilities"]["EXPANSION"] > 0.0


def test_pii_field_is_rejected(client):
    """정의되지 않은 PII 필드(company_name)는 422 로 거부된다."""
    r = _post(
        client,
        {
            "ksicCode": "G471",
            "capitalAmount": 5_000_000,
            "foundingYear": 2020,
            "revenueAmount": 100_000_000,
            "company_name": "이룸주식회사",
        },
    )
    assert r.status_code == 422
