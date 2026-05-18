package kr.co.ircp.cms.infra.ml;

import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;

/**
 * 내부 ML 추론 서비스 클라이언트.
 *
 * <p>SPEC-CMS-AI-001 — Python FastAPI ML 서비스(내부망 전용)와 통신.
 * 계약: {@code docs/ai-ml-service-openapi.yaml}.
 * 요청에는 PII가 포함되지 않는다 (ksicCode/capitalAmount/foundingYear/revenueAmount만).
 */
// @MX:ANCHOR: [AUTO] MlServiceClient — ML 추론 외부 통합 경계
// @MX:REASON: GrowthStage/RiskScore/Simulation 서비스가 공통으로 의존하는 외부 시스템 진입점 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-AI-001
public interface MlServiceClient {

    GrowthStageResponse predictGrowthStage(GrowthStageRequest request);

    RiskScoreResponse predictRiskScore(RiskScoreRequest request);

    SimulationResponse predictSimulation(SimulationRequest request);

    MlHealthResponse health();
}
