"""POST /ml/v1/rag — 템플릿 기반 생성형 답변 (LLM 비의존).

[보안/품질] contexts 가 비어 있으면 환각 방지를 위해 고정 안내 문구와
qualityScore=null 을 반환한다 (AC: 빈 컨텍스트 환각 가드).
"""

from __future__ import annotations

import re
from typing import List

from fastapi import APIRouter

from app.embedder import cosine_similarity, get_embedder
from app.schemas import RagRequest, RagResponse, RagSource

router = APIRouter()

MODEL_ID = "rag-template-v1"

_EMPTY_ANSWER = "관련 정책을 찾지 못했습니다."
_TOKEN_RE = re.compile(r"[0-9A-Za-z가-힣]+")


def _relevance(question_vec, content: str) -> float:
    cvec = get_embedder().encode(content)
    return round(cosine_similarity(question_vec, cvec), 4)


def _quality_score(question: str, contexts) -> int:
    """질문 토큰이 컨텍스트 본문에 포함된 비율 기반 0~100 품질 점수."""
    q_tokens = {t for t in _TOKEN_RE.findall(question) if len(t) >= 2}
    if not q_tokens:
        return 60
    joined = " ".join(c.title + " " + c.content for c in contexts)
    hit = sum(1 for t in q_tokens if t in joined)
    ratio = hit / len(q_tokens)
    # 컨텍스트 존재 자체로 최소 50점 보장, 토큰 매칭으로 가산
    return int(round(50 + ratio * 50))


@router.post("/ml/v1/rag", response_model=RagResponse)
def rag(req: RagRequest) -> RagResponse:
    """검색된 정책 컨텍스트로 템플릿 답변을 생성한다."""
    if not req.contexts:
        # 환각 가드: 컨텍스트 없으면 단정 답변 금지
        return RagResponse(answer=_EMPTY_ANSWER, sources=[], qualityScore=None)

    q_vec = get_embedder().encode(req.question)
    sources: List[RagSource] = []
    titles: List[str] = []
    for ctx in req.contexts:
        rel = _relevance(q_vec, ctx.content)
        sources.append(RagSource(id=ctx.id, relevance=rel))
        titles.append(ctx.title)

    sources.sort(key=lambda s: s.relevance, reverse=True)
    title_list = ", ".join(f"[{t}]" for t in titles)
    answer = (
        f"관련 정책: {title_list}. "
        f"질문하신 '{req.question}' 에 대해 위 정책들을 참고하시기 바랍니다."
    )
    return RagResponse(
        answer=answer,
        sources=sources,
        qualityScore=_quality_score(req.question, req.contexts),
    )
