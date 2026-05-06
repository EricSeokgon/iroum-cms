package kr.co.ircp.cms.domain.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 위젯 데이터 응답 (ECharts 포맷).
 * REQ-VIZ-005-D-1
 *
 * <p>응답 스키마:
 * <pre>
 * {
 *   "widget": {"id":12,"code":"PV_BY_FEATURE","type":"BAR_CHART"},
 *   "available_dimensions": ["period","feature","industry"],
 *   "applied_filter": {"period":"7d","feature":["board"]},
 *   "dataset": {"categories":[...],"series":[{"name":"PV","data":[...]}]},
 *   "generated_at":"2026-04-29T10:30:00Z",
 *   "cache_hit": true
 * }
 * </pre>
 */
public record WidgetDataResponse(
        WidgetSummary widget,
        List<String> availableDimensions,
        Map<String, Object> appliedFilter,
        Dataset dataset,
        Instant generatedAt,
        boolean cacheHit
) {
    public record WidgetSummary(Long id, String code, String type) {}

    public record Series(String name, List<Object> data) {}

    public record Dataset(List<String> categories, List<Series> series) {}
}
