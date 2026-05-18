"""POST /ml/v1/growth-stage — 성장단계 예측."""

from __future__ import annotations

from fastapi import APIRouter

from app import predictor
from app.schemas import GrowthStageResponse, PredictionRequest

router = APIRouter()

MODEL_ID = "growth-stage-rule-v1"


@router.post("/ml/v1/growth-stage", response_model=GrowthStageResponse)
def growth_stage(req: PredictionRequest) -> GrowthStageResponse:
    """규칙 기반 성장단계 분류 결과를 반환한다."""
    stage, probs, confidence = predictor.predict_growth_stage(
        req.ksicCode, req.capitalAmount, req.foundingYear, req.revenueAmount
    )
    return GrowthStageResponse(
        stage=stage,
        entryProbabilities=probs,
        confidence=confidence,
        modelVersion=predictor.MODEL_VERSION,
    )
