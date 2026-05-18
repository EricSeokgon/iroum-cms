"""POST /ml/v1/simulation 명세 테스트."""

from __future__ import annotations

VALID_STAGES = {"SEED", "STARTUP", "GROWTH", "EXPANSION", "MATURITY"}


def _post(client):
    return client.post(
        "/ml/v1/simulation",
        json={
            "ksicCode": "G471",
            "capitalAmount": 50_000_000,
            "foundingYear": 2022,
            "revenueAmount": 200_000_000,
        },
    )


def test_response_has_exact_camelcase_fields(client):
    """응답이 Java SimulationResponse camelCase 필드와 일치한다."""
    body = _post(client).json()
    assert set(body.keys()) == {"projection", "modelVersion"}
    point = body["projection"][0]
    assert set(point.keys()) == {"year", "stage", "entryProbabilities"}
    assert body["modelVersion"] == "rule-v1.0.0"


def test_at_least_two_projection_points(client):
    """투영 포인트가 2개 이상이다."""
    body = _post(client).json()
    assert len(body["projection"]) >= 2


def test_years_strictly_increasing(client):
    """투영 연도가 오름차순이며 중복이 없다."""
    points = _post(client).json()["projection"]
    years = [p["year"] for p in points]
    assert years == sorted(years)
    assert len(set(years)) == len(years)


def test_each_point_valid_stage_and_probabilities_sum_one(client):
    """각 포인트의 stage 가 enum 이고 확률 합이 ~1.0 이다."""
    for p in _post(client).json()["projection"]:
        assert p["stage"] in VALID_STAGES
        probs = p["entryProbabilities"]
        assert abs(sum(probs.values()) - 1.0) < 1e-3
