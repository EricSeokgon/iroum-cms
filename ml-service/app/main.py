"""iroum-cms ML 추론 서비스 FastAPI 진입점 (SPEC-CMS-ML-SERVICE-001).

7개 엔드포인트:
  POST /ml/v1/growth-stage   성장단계 예측
  POST /ml/v1/risk-score     위험도 점수
  POST /ml/v1/simulation     성장 시뮬레이션
  POST /ml/v1/policy-match   정책 시맨틱 매칭
  POST /ml/v1/embed          문장 임베딩 (384d)
  POST /ml/v1/rag            생성형 답변
  GET  /ml/v1/health         헬스 체크

[보안] 모든 요청 스키마는 extra=forbid — PII/미정의 필드 거부.
"""

from __future__ import annotations

import logging
import os

from fastapi import FastAPI

from app import __version__
from app.routers import (
    embed,
    growth_stage,
    health,
    policy_match,
    rag,
    risk_score,
    simulation,
)

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)

app = FastAPI(
    title="iroum-cms ML Service",
    version=__version__,
    description="규칙 기반 예측 + sentence-transformers 임베딩 ML 추론 서비스",
)

app.include_router(growth_stage.router, tags=["prediction"])
app.include_router(risk_score.router, tags=["prediction"])
app.include_router(simulation.router, tags=["prediction"])
app.include_router(policy_match.router, tags=["policy"])
app.include_router(embed.router, tags=["embedding"])
app.include_router(rag.router, tags=["rag"])
app.include_router(health.router, tags=["health"])
