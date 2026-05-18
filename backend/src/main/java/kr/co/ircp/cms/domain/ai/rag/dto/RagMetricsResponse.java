package kr.co.ircp.cms.domain.ai.rag.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * RAG 품질 모니터링 지표 응답.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015 — 만족도 비율(HELPFUL/전체 피드백), 캐시 히트율,
 * 평균 응답시간, degraded 비율, 일자별 시계열.
 *
 * @param satisfactionRate HELPFUL / (HELPFUL+UNHELPFUL)
 * @param cacheHitRate     캐시 히트 질의 / 전체 질의
 * @param avgLatencyMs     평균 응답시간(ms)
 * @param degradedRate     degraded 질의 / 전체 질의
 * @param totalQueries     기간 내 전체 질의 수
 * @param timeSeries       일자별 시계열
 */
public record RagMetricsResponse(
        double satisfactionRate,
        double cacheHitRate,
        double avgLatencyMs,
        double degradedRate,
        long totalQueries,
        List<TimeSeriesPoint> timeSeries) {

    /**
     * 일자별 RAG 질의 시계열 포인트.
     *
     * @param date             집계 일자
     * @param queryCount       해당 일자 질의 수
     * @param satisfactionRate 해당 일자 만족도 비율
     */
    public record TimeSeriesPoint(LocalDate date, long queryCount, double satisfactionRate) {
    }
}
