"""POST /ml/v1/embed 명세 테스트."""

from __future__ import annotations


def _post(client, text):
    return client.post("/ml/v1/embed", json={"text": text})


def test_vector_length_is_exactly_384(client):
    """vector 가 정확히 384개 요소를 갖는다."""
    r = _post(client, "정책 질문 내용")
    assert r.status_code == 200
    body = r.json()
    assert set(body.keys()) == {"vector"}
    assert len(body["vector"]) == 384


def test_all_elements_are_floats(client):
    """vector 의 모든 요소가 float 이다."""
    vec = _post(client, "청년 창업 지원 정책").json()["vector"]
    assert all(isinstance(x, float) for x in vec)


def test_same_text_is_deterministic(client):
    """동일 텍스트는 동일 벡터를 반환한다 (결정론적)."""
    a = _post(client, "동일 입력 테스트").json()["vector"]
    b = _post(client, "동일 입력 테스트").json()["vector"]
    assert a == b


def test_empty_text_still_returns_384(client):
    """빈 문자열도 384차원 벡터를 반환한다."""
    vec = _post(client, "").json()["vector"]
    assert len(vec) == 384


def test_extra_field_rejected(client):
    """text 외 식별 필드(user_id)는 422 로 거부된다."""
    r = client.post(
        "/ml/v1/embed", json={"text": "질문", "user_id": "u-123"}
    )
    assert r.status_code == 422
