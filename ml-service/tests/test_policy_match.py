"""POST /ml/v1/policy-match 명세 테스트."""

from __future__ import annotations


def _profile():
    return {
        "ksic_code": "G471",
        "employee_count": 10,
        "growth_stage": "GROWTH",
        "region_code": "11",
        "annual_revenue": 100_000_000,
    }


def _post(client, **overrides):
    body = {
        "companyProfile": _profile(),
        "queryText": "창업 지원",
        "candidatePolicyIds": [1, 2, 3],
        "topK": 3,
    }
    body.update(overrides)
    return client.post("/ml/v1/policy-match", json=body)


def test_response_camelcase_field_names(client):
    """응답 필드명이 Java MlPolicyMatchResponse/Item/Explanation 와 일치한다."""
    r = _post(client)
    assert r.status_code == 200
    body = r.json()
    assert set(body.keys()) == {"matches", "modelName", "modelVersion"}
    assert body["modelName"] == "sentence-transformers"
    assert body["modelVersion"] == "1.0.0"
    item = body["matches"][0]
    assert set(item.keys()) == {"policyId", "semanticScore", "explanation"}
    assert set(item["explanation"].keys()) == {"matchedTerms", "rationale"}


def test_semantic_score_in_zero_one_range(client):
    """모든 semanticScore 가 0.0~1.0 범위이다."""
    matches = _post(client).json()["matches"]
    for m in matches:
        assert 0.0 <= m["semanticScore"] <= 1.0


def test_topk_limits_result_count(client):
    """topK 가 결과 개수 상한을 적용한다."""
    r = _post(client, candidatePolicyIds=[1, 2, 3, 4, 5, 6], topK=2)
    assert len(r.json()["matches"]) == 2


def test_results_sorted_by_score_desc(client):
    """matches 가 semanticScore 내림차순으로 정렬된다."""
    matches = _post(
        client, candidatePolicyIds=[1, 2, 3, 4, 5], topK=5
    ).json()["matches"]
    scores = [m["semanticScore"] for m in matches]
    assert scores == sorted(scores, reverse=True)


def test_company_profile_extra_key_rejected(client):
    """companyProfile 에 정의되지 않은 키(company_name)는 422 거부."""
    bad = _profile()
    bad["company_name"] = "이룸"
    r = client.post(
        "/ml/v1/policy-match",
        json={
            "companyProfile": bad,
            "queryText": "창업 지원",
            "candidatePolicyIds": [1],
            "topK": 1,
        },
    )
    assert r.status_code == 422
