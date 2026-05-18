"""POST /ml/v1/risk-score — 사업 위험도 점수 예측."""

from __future__ import annotations

from fastapi import APIRouter

from app import predictor
from app.schemas import PredictionRequest, RiskFactor, RiskScoreResponse

router = APIRouter()

MODEL_ID = "risk-score-rule-v1"


@router.post("/ml/v1/risk-score", response_model=RiskScoreResponse)
def risk_score(req: PredictionRequest) -> RiskScoreResponse:
    """규칙 기반 부도확률/위험등급/상위 기여요인을 반환한다."""
    default_prob, grade, top_factors = predictor.predict_risk_score(
        req.ksicCode, req.capitalAmount, req.foundingYear, req.revenueAmount
    )
    return RiskScoreResponse(
        defaultProbability=default_prob,
        riskGrade=grade,
        topFactors=[
            RiskFactor(name=name, contribution=contribution)
            for name, contribution in top_factors
        ],
        modelVersion=predictor.MODEL_VERSION,
    )
