package kr.co.ircp.cms.infra.ml;

import kr.co.ircp.cms.infra.ml.dto.EmbedRequest;
import kr.co.ircp.cms.infra.ml.dto.EmbedResponse;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import kr.co.ircp.cms.infra.ml.dto.MlMatchExplanation;
import kr.co.ircp.cms.infra.ml.dto.MlMatchItem;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchRequest;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchResponse;
import kr.co.ircp.cms.infra.ml.dto.RagContextItem;
import kr.co.ircp.cms.infra.ml.dto.RagRequest;
import kr.co.ircp.cms.infra.ml.dto.RagResponse;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** policyMatch 호출 횟수 (캐시 hit 검증용 — AC-PM-003). */
    private final AtomicInteger policyMatchCalls = new AtomicInteger(0);

    /** embed 호출 횟수 (캐시 hit 검증용 — AC-RAG-003). */
    private final AtomicInteger embedCalls = new AtomicInteger(0);

    /** rag 호출 횟수 (캐시 hit 검증용 — AC-RAG-003). */
    private final AtomicInteger ragCalls = new AtomicInteger(0);

    /** embed 단계만 실패 시뮬레이션 (AC-RAG-007 — 임베딩 실패 → FTS 폴백). */
    private volatile boolean embedFails = false;

    /** embed 단계만 강제 실패하도록 토글한다(rag/FTS는 정상). */
    public void simulateEmbedFailure(boolean enabled) {
        this.embedFails = enabled;
    }

    /** embed 호출 횟수 (캐시 미스 시 1, 캐시 hit 시 추가 호출 없음). */
    public int embedCallCount() {
        return embedCalls.get();
    }

    /** rag 호출 횟수. */
    public int ragCallCount() {
        return ragCalls.get();
    }

    /** RAG 관련 호출 카운터·실패 토글 초기화. */
    public void resetRagCounters() {
        embedCalls.set(0);
        ragCalls.set(0);
        embedFails = false;
    }

    /** 후보별 고정 시맨틱 점수. null이면 결정적 기본 점수(0.5) 사용. */
    private volatile Map<Long, Double> fixedSemanticScores;

    /** 타임아웃 시뮬레이션 토글 (타임아웃 폴백 경로 검증용). */
    public void simulateTimeout(boolean enabled) {
        this.timeout = enabled;
    }

    /** policyMatch 호출 횟수 반환 (캐시 미스 시 1, 캐시 hit 시 추가 호출 없음). */
    public int policyMatchCallCount() {
        return policyMatchCalls.get();
    }

    /** 호출 카운터 초기화. */
    public void resetPolicyMatchCallCount() {
        policyMatchCalls.set(0);
    }

    /** 후보 정책 ID → 시맨틱 점수 고정 (하이브리드 점수 계산 검증용 — AC-PM-006). */
    public void setFixedSemanticScores(Map<Long, Double> scores) {
        this.fixedSemanticScores = scores;
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
    public MlPolicyMatchResponse policyMatch(MlPolicyMatchRequest request) {
        policyMatchCalls.incrementAndGet();
        guardTimeout();
        List<Long> candidates = request.candidatePolicyIds() == null
                ? List.of() : request.candidatePolicyIds();
        List<MlMatchItem> matches = candidates.stream()
                .map(id -> new MlMatchItem(
                        id,
                        semanticScoreFor(id),
                        new MlMatchExplanation(
                                List.of("ksic", "growth-stage"),
                                "프로필 특성과 정책 대상 요건의 시맨틱 유사도 기반 매칭")))
                .toList();
        return new MlPolicyMatchResponse(matches, "mock-policy-match", "1.0.0");
    }

    private double semanticScoreFor(Long policyId) {
        if (fixedSemanticScores != null && fixedSemanticScores.containsKey(policyId)) {
            return fixedSemanticScores.get(policyId);
        }
        return 0.5;
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        embedCalls.incrementAndGet();
        if (embedFails) {
            throw new MlServiceException("ml-service embed failure (simulated)");
        }
        guardTimeout();
        // 결정적 384차원 벡터 — 텍스트 해시 기반 (테스트 재현성).
        int seed = request == null || request.text() == null ? 0 : request.text().hashCode();
        List<Float> vector = new java.util.ArrayList<>(384);
        for (int i = 0; i < 384; i++) {
            vector.add((float) (((seed + i) % 100) / 100.0));
        }
        return new EmbedResponse(vector);
    }

    @Override
    public RagResponse rag(RagRequest request) {
        ragCalls.incrementAndGet();
        guardTimeout();
        List<RagContextItem> contexts = request == null || request.contexts() == null
                ? List.of() : request.contexts();
        if (contexts.isEmpty()) {
            // 빈 컨텍스트 → 환각 금지 안내 (AC-RAG-008).
            return new RagResponse("관련 정책을 찾지 못했습니다.", List.of(), null);
        }
        StringBuilder answer = new StringBuilder("질문에 대한 관련 정책 안내: ");
        List<RagResponse.Source> sources = new java.util.ArrayList<>();
        int rank = contexts.size();
        for (RagContextItem ctx : contexts) {
            answer.append('[').append(ctx.title()).append("] ");
            // 상위 컨텍스트일수록 높은 관련도 (결정적).
            sources.add(new RagResponse.Source(ctx.id(), rank / (double) contexts.size()));
            rank--;
        }
        return new RagResponse(answer.toString().trim(), sources, 88);
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
