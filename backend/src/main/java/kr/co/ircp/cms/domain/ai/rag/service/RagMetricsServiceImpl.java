package kr.co.ircp.cms.domain.ai.rag.service;

import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsAggregate;
import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsQuery;
import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsResponse;
import kr.co.ircp.cms.domain.ai.rag.dto.RagTimeSeriesRow;
import kr.co.ircp.cms.domain.ai.rag.repository.RagQueryLogRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RAG 품질 모니터링 서비스 구현.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015 — 매퍼 원시 집계 → 비율 계산(0-나눗셈 가드).
 * AI-002 {@code PolicyMatchAdminService} 보정 패턴 준용.
 */
@Service
public class RagMetricsServiceImpl implements RagMetricsService {

    private final RagQueryLogRepository repository;

    public RagMetricsServiceImpl(RagQueryLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public RagMetricsResponse getMetrics(RagMetricsQuery query) {
        RagMetricsAggregate agg = repository.aggregateMetrics(query);
        long total = agg.totalQueries();

        double satisfaction = ratio(agg.helpfulCount(), agg.feedbackCount());
        double cacheHit = ratio(agg.cacheHitCount(), total);
        double degradedRate = ratio(agg.degradedCount(), total);
        double avgLatency = total == 0 ? 0.0
                : round((double) agg.latencySumMs() / total);

        List<RagMetricsResponse.TimeSeriesPoint> series =
                repository.timeSeries(query).stream()
                        .map(this::toPoint)
                        .toList();

        return new RagMetricsResponse(
                satisfaction, cacheHit, avgLatency, degradedRate, total, series);
    }

    private RagMetricsResponse.TimeSeriesPoint toPoint(RagTimeSeriesRow row) {
        return new RagMetricsResponse.TimeSeriesPoint(
                row.day(), row.queryCount(),
                ratio(row.helpfulCount(), row.feedbackCount()));
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0
                : round((double) numerator / denominator);
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
