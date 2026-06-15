package kr.co.ircp.cms.infra.ml;

import kr.co.ircp.cms.infra.ml.dto.EmbedRequest;
import kr.co.ircp.cms.infra.ml.dto.EmbedResponse;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchRequest;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchResponse;
import kr.co.ircp.cms.infra.ml.dto.RagRequest;
import kr.co.ircp.cms.infra.ml.dto.RagResponse;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationRequest;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationResponse;

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

    /**
     * 정책 후보 풀에 대한 시맨틱 매칭 점수 산출 (SPEC-CMS-AI-002).
     *
     * <p>요청에는 PII가 포함되지 않는다(ksic_code/employee_count/growth_stage/region_code/annual_revenue).
     * 호출 실패·타임아웃·CircuitBreaker OPEN 시 {@link MlServiceException}을 던져
     * 호출부의 규칙 단독 폴백으로 위임한다(REQ-PM-009).
     */
    MlPolicyMatchResponse policyMatch(MlPolicyMatchRequest request);

    /**
     * 질문 텍스트를 384차원 임베딩 벡터로 변환 (SPEC-CMS-AI-003 REQ-RAG-002).
     *
     * <p>요청에는 질문 텍스트만 포함된다(PII 없음 — AC-RAG-005).
     * 호출 실패·타임아웃·CircuitBreaker OPEN 시 {@link MlServiceException}을 던져
     * 호출부의 FTS 단독 폴백으로 위임한다(REQ-RAG-009).
     */
    // @MX:NOTE: [AUTO] embed — ML 서비스 계약 경계 (질문 텍스트만 전송, PII 미포함)
    // @MX:SPEC: SPEC-CMS-AI-003
    EmbedResponse embed(EmbedRequest request);

    /**
     * 정책 컨텍스트 기반 생성형 답변 (SPEC-CMS-AI-003 REQ-RAG-004).
     *
     * <p>요청에는 질문·정책 컨텍스트만 포함된다(사용자 식별정보 없음 — AC-RAG-005).
     * 호출 실패·타임아웃·CircuitBreaker OPEN 시 {@link MlServiceException}을 던져
     * 호출부의 FTS 단독 폴백으로 위임한다(REQ-RAG-008).
     */
    // @MX:NOTE: [AUTO] rag — ML 서비스 계약 경계 (질문·정책 컨텍스트만 전송, PII 미포함)
    // @MX:SPEC: SPEC-CMS-AI-003
    RagResponse rag(RagRequest request);

    /**
     * 본문 텍스트 기반 태그 추천 (SPEC-CMS-AI-004 REQ-AI-TAG-001/002).
     *
     * <p>요청에는 본문 텍스트와 기존 선택 태그만 포함된다(작성자 식별정보 없음 — AC-AI-TAG-007).
     * 호출 실패·타임아웃·CircuitBreaker OPEN 시 {@link MlServiceException}을 던져
     * 호출부의 그레이스풀 폴백(빈 추천 배열 200)으로 위임한다(REQ-AI-TAG-003/009).
     */
    // @MX:ANCHOR: [AUTO] tagRecommendation — ML 태그 추천 계약 경계 (본문 텍스트만 전송, PII 미포함)
    // @MX:REASON: 서비스·모킹·통합 테스트가 공통 호출하는 외부 통합 진입점 (fan_in 예상 3+)
    // @MX:SPEC: SPEC-CMS-AI-004
    TagRecommendationResponse tagRecommendation(TagRecommendationRequest request);

    MlHealthResponse health();
}
