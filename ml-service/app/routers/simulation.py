"""POST /ml/v1/simulation — 연도별 성장단계 시뮬레이션."""

from __future__ import annotations

from fastapi import APIRouter

from app import predictor
from app.schemas import PredictionRequest, ProjectionPoint, SimulationResponse

router = APIRouter()

MODEL_ID = "simulation-rule-v1"


@router.post("/ml/v1/simulation", response_model=SimulationResponse)
def simulation(req: PredictionRequest) -> SimulationResponse:
    """현재 상태 기준 +1/+3/+5 년 성장단계 투영을 반환한다."""
    points = predictor.simulate(
        req.ksicCode, req.capitalAmount, req.foundingYear, req.revenueAmount
    )
    return SimulationResponse(
        projection=[
            ProjectionPoint(year=year, stage=stage, entryProbabilities=probs)
            for year, stage, probs in points
        ],
        modelVersion=predictor.MODEL_VERSION,
    )
