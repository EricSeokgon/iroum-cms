"""POST /ml/v1/rag 명세 테스트."""

from __future__ import annotations


def test_response_camelcase_fields_with_contexts(client):
    """컨텍스트 존재 시 answer/sources/qualityScore 를 반환한다."""
    r = client.post(
        "/ml/v1/rag",
        json={
            "question": "창업 지원 정책이 있나요?",
            "contexts": [
                {
                    "id": 1,
                    "title": "청년 창업 지원",
                    "content": "만 39세 이하 청년 창업자 대상 자금 지원 정책입니다.",
                }
            ],
        },
    )
    assert r.status_code == 200
    body = r.json()
    assert set(body.keys()) == {"answer", "sources", "qualityScore"}
    assert body["answer"]
    assert len(body["sources"]) == 1
    src = body["sources"][0]
    assert set(src.keys()) == {"id", "relevance"}
    assert 0.0 <= src["relevance"] <= 1.0
    assert isinstance(body["qualityScore"], int)
    assert 0 <= body["qualityScore"] <= 100


def test_empty_contexts_hallucination_guard(client):
    """빈 컨텍스트 시 환각 방지 고정 문구 + qualityScore=null 반환."""
    r = client.post(
        "/ml/v1/rag",
        json={"question": "존재하지 않는 정책?", "contexts": []},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["answer"] == "관련 정책을 찾지 못했습니다."
    assert body["sources"] == []
    assert body["qualityScore"] is None


def test_answer_references_context_titles(client):
    """생성 답변이 컨텍스트 제목을 포함한다 (환각 아닌 근거 기반)."""
    r = client.post(
        "/ml/v1/rag",
        json={
            "question": "지원 정책",
            "contexts": [
                {"id": 7, "title": "수출 바우처", "content": "수출 마케팅 비용 지원."},
                {"id": 8, "title": "R&D 지원", "content": "기술 개발 자금 지원."},
            ],
        },
    )
    body = r.json()
    assert "수출 바우처" in body["answer"]
    assert "R&D 지원" in body["answer"]
    assert {s["id"] for s in body["sources"]} == {7, 8}


def test_extra_identifier_field_rejected(client):
    """RagRequest 에 정의되지 않은 식별 필드는 422 거부."""
    r = client.post(
        "/ml/v1/rag",
        json={
            "question": "정책?",
            "contexts": [],
            "member_id": "m-1",
        },
    )
    assert r.status_code == 422


def test_context_extra_field_rejected(client):
    """컨텍스트 항목에 id/title/content 외 키가 있으면 422 거부."""
    r = client.post(
        "/ml/v1/rag",
        json={
            "question": "정책?",
            "contexts": [
                {
                    "id": 1,
                    "title": "t",
                    "content": "c",
                    "owner_ssn": "900101-1234567",
                }
            ],
        },
    )
    assert r.status_code == 422
