"""POST /ml/v1/risk-score 명세 테스트."""

from __future__ import annotations

VALID_GRADES = {"GREEN", "YELLOW", "ORANGE", "RED"}


def _post(client, body):
    return client.post("/ml/v1/risk-score", json=body)


def test_response_has_exact_camelcase_fields(client):
    """응답이 Java RiskScoreResponse camelCase 필드와 정확히 일치한다."""
    r = _post(
        client,
        {
            "ksicCode": "G471",
            "capitalAmount": 50_000_000,
            "foundingYear": 2018,
            "revenueAmount": 300_000_000,
        },
    )
    assert r.status_code == 200
    body = r.json()
    assert set(body.keys()) == {
        "defaultProbability",
        "riskGrade",
        "topFactors",
        "modelVersion",
    }
    factor = body["topFactors"][0]
    assert set(factor.keys()) == {"name", "contribution"}


def test_default_probability_in_range_and_grade_consistent(client):
    """defaultProbability 0~1, riskGrade 가 임계값과 일관된다."""
    r = _post(
        client,
        {
            "ksicCode": "I560",
            "capitalAmount": 5_000_000,
            "foundingYear": 2025,
            "revenueAmount": 10_000_000,
        },
    )
    body = r.json()
    p = body["defaultProbability"]
    assert 0.0 <= p <= 1.0
    assert body["riskGrade"] in VALID_GRADES
    if p < 0.3:
        assert body["riskGrade"] == "GREEN"
    elif p < 0.5:
        assert body["riskGrade"] == "YELLOW"
    elif p < 0.7:
        assert body["riskGrade"] == "ORANGE"
    else:
        assert body["riskGrade"] == "RED"


def test_top_factors_max_three_and_sorted(client):
    """topFactors 는 최대 3개이며 contribution 내림차순이다."""
    r = _post(
        client,
        {
            "ksicCode": "K640",
            "capitalAmount": 100_000_000,
            "foundingYear": 2012,
            "revenueAmount": 600_000_000,
        },
    )
    factors = r.json()["topFactors"]
    assert 1 <= len(factors) <= 3
    contribs = [f["contribution"] for f in factors]
    assert contribs == sorted(contribs, reverse=True)


def test_low_capital_new_company_is_high_risk(client):
    """자본 작고 신생 기업은 위험도가 GREEN 보다 높다."""
    r = _post(
        client,
        {
            "ksicCode": "I560",
            "capitalAmount": 1_000_000,
            "foundingYear": 2025,
            "revenueAmount": 1_000_000,
        },
    )
    assert r.json()["defaultProbability"] >= 0.3


def test_strong_company_is_lower_risk(client):
    """자본 크고 업력 긴 기업은 신생 영세 기업보다 위험도가 낮다."""
    strong = _post(
        client,
        {
            "ksicCode": "K640",
            "capitalAmount": 500_000_000,
            "foundingYear": 2005,
            "revenueAmount": 5_000_000_000,
        },
    ).json()["defaultProbability"]
    weak = _post(
        client,
        {
            "ksicCode": "I560",
            "capitalAmount": 1_000_000,
            "foundingYear": 2025,
            "revenueAmount": 1_000_000,
        },
    ).json()["defaultProbability"]
    assert strong < weak


def test_pii_field_rejected(client):
    """ceo_name 같은 식별 필드는 422 로 거부된다."""
    r = _post(
        client,
        {
            "ksicCode": "G471",
            "capitalAmount": 50_000_000,
            "foundingYear": 2018,
            "revenueAmount": 300_000_000,
            "ceo_name": "홍길동",
        },
    )
    assert r.status_code == 422
