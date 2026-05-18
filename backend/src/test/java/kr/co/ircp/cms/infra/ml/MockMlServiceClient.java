package kr.co.ircp.cms.infra.ml;

import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;

import java.util.List;
import java.util.Map;

/**
 * 테스트 전용 ML 서비스 클라이언트 스텁.
 *
 * <p>SPEC-CMS-AI-001 — OpenAPI 계약(docs/ai-ml-service-openapi.yaml)에 정합하는
 * 결정적(deterministic) 응답을 반환한다. {@code simulateTimeout(true)} 설정 시
 * 모든 예측 호출이 {@link MlServiceException}(timeout)을 던진다.
 *
 * <p>SPEC 1D 준수: src/test/java 에만 존재하므로 운영 빌드(production jar)에
 * 컴파일/포함되지 않는다. 순수 테스트 더블이라 Spring 빈 등록(@Component/@Profile) 없이
 * 직접 인스턴스화하여 사용한다. Step 2+ 통합 테스트에서 빈으로 필요할 경우
 * 테스트 전용 @TestConfiguration 에서 명시적으로 등록한다.
 */
public class MockMlServiceClient implements MlServiceClient {

    private volatile boolean timeout = false;

    /** 타임아웃 시뮬레이션 토글 (타임아웃 폴백 경로 검증용). */
    public void simulateTimeout(boolean enabled) {
        this.timeout = enabled;
    }

    @Override
    public GrowthStageResponse predictGrowthStage(GrowthStageRequest request) {
        guardTimeout();
        return new GrowthStageResponse(
                "GROWTH",
                Map.of("GROWTH", 0.62, "EXPANSION", 0.21, "STARTUP", 0.17),
                0.62,
                "mock-growth-1.0.0");
    }

    @Override
    public RiskScoreResponse predictRiskScore(RiskScoreRequest request) {
        guardTimeout();
        return new RiskScoreResponse(
                0.18,
                "YELLOW",
                List.of(
                        new RiskScoreResponse.RiskFactor("capitalAdequacy", 0.41),
                        new RiskScoreResponse.RiskFactor("industryVolatility", 0.33),
                        new RiskScoreResponse.RiskFactor("foundingTenure", 0.26)),
                "mock-risk-2.1.0");
    }

    @Override
    public SimulationResponse predictSimulation(SimulationRequest request) {
        guardTimeout();
        int baseYear = request.foundingYear() != null ? request.foundingYear() : 2020;
        return new SimulationResponse(
                List.of(
                        new SimulationResponse.ProjectionPoint(
                                baseYear + 1, "STARTUP", Map.of("STARTUP", 0.7, "GROWTH", 0.3)),
                        new SimulationResponse.ProjectionPoint(
                                baseYear + 3, "GROWTH", Map.of("GROWTH", 0.6, "EXPANSION", 0.4))),
                "mock-sim-1.0.0");
    }

    @Override
    public MlHealthResponse health() {
        guardTimeout();
        return new MlHealthResponse(
                "UP",
                List.of("growth-stage-clf", "risk-score-model", "simulation-model"));
    }

    private void guardTimeout() {
        if (timeout) {
            throw new MlServiceException("ml-service timeout (simulated)");
        }
    }
}
