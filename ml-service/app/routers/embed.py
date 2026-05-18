"""POST /ml/v1/embed — 문장 임베딩 (정확히 384차원)."""

from __future__ import annotations

from fastapi import APIRouter

from app.embedder import EMBEDDING_DIM, get_embedder
from app.schemas import EmbedRequest, EmbedResponse

router = APIRouter()

MODEL_ID = "sentence-transformers-384d"


@router.post("/ml/v1/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    """질문 텍스트의 384차원 임베딩 벡터를 반환한다."""
    vec = get_embedder().encode(req.text)
    values = [float(x) for x in vec.tolist()]
    # 계약상 정확히 384차원 보장 (방어적 길이 보정)
    if len(values) != EMBEDDING_DIM:
        if len(values) > EMBEDDING_DIM:
            values = values[:EMBEDDING_DIM]
        else:
            values = values + [0.0] * (EMBEDDING_DIM - len(values))
    return EmbedResponse(vector=values)
