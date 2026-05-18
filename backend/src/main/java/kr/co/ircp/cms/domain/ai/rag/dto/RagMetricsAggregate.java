package kr.co.ircp.cms.domain.ai.rag.dto;

/**
 * RAG 메트릭 집계 원시 행 (MyBatis resultType).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015 — 매퍼가 COUNT/SUM으로 집계한 원시 수치.
 * 비율 계산은 서비스 레이어에서 0-나눗셈 가드와 함께 수행한다.
 *
 * @param totalQueries  전체 질의 수
 * @param helpfulCount  HELPFUL 피드백 수
 * @param feedbackCount 피드백 총 수 (HELPFUL+UNHELPFUL)
 * @param cacheHitCount 캐시 히트 질의 수
 * @param degradedCount degraded 질의 수
 * @param latencySumMs  응답시간 합(ms)
 */
public record RagMetricsAggregate(
        long totalQueries,
        long helpfulCount,
        long feedbackCount,
        long cacheHitCount,
        long degradedCount,
        long latencySumMs) {
}
