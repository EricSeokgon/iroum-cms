"""GET /ml/v1/health — ML 서비스 헬스 체크."""

from __future__ import annotations

from fastapi import APIRouter

from app.schemas import HealthResponse

router = APIRouter()

LOADED_MODELS = [
    "growth-stage-rule-v1",
    "risk-score-rule-v1",
    "simulation-rule-v1",
    "policy-match-embed-v1",
    "sentence-transformers-384d",
    "rag-template-v1",
]


@router.get("/ml/v1/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """서비스 상태와 로드된 모델 목록을 반환한다."""
    return HealthResponse(status="UP", loadedModels=LOADED_MODELS)
