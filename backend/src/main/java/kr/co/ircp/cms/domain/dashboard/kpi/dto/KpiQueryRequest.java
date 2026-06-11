package kr.co.ircp.cms.domain.dashboard.kpi.dto;

import java.time.LocalDate;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 요청 파라미터.
 *
 * <p>실제 스키마(kpi_value) 기준 매핑:
 * <ul>
 *   <li>{@code kpiCode} : kpi_definition.code 필터(선택). null 이면 전체 KPI.</li>
 *   <li>{@code fromDate}/{@code toDate} : kpi_value.calculated_at 범위 필터(선택,
 *       kpi_value 에 별도 일자 컬럼이 없으므로 집계 시각 기준).</li>
 *   <li>{@code dimensionJson} : dimension JSONB containment(@>) 필터(선택).</li>
 *   <li>{@code granularity} : daily/weekly/monthly/quarterly/yearly. dimension JSONB 의
 *       해당 키 존재 여부(jsonb_exists)로 필터. 기본 null(전체).</li>
 *   <li>{@code page}/{@code size} : 페이지네이션. size 는 1~1000 으로 강제 클램프.</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] KpiQueryRequest — KPI 조회 필터 계약. size 상한 1000 (REQ-KPI-002 AC-019)
public record KpiQueryRequest(
        String kpiCode,
        LocalDate fromDate,
        LocalDate toDate,
        String dimensionJson,
        String granularity,
        int page,
        int size
) {

    /** REQ-KPI-002 AC-019: 조회 결과 안전 상한. */
    public static final int MAX_SIZE = 1000;
    public static final int DEFAULT_SIZE = 100;

    /**
     * 입력 정규화: page 음수 방지, size 를 1~1000 으로 클램프(0/미지정은 기본값 100).
     */
    public KpiQueryRequest normalize() {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new KpiQueryRequest(
                blankToNull(kpiCode), fromDate, toDate,
                blankToNull(dimensionJson), normalizeGranularity(granularity),
                safePage, safeSize);
    }

    /** granularity → dimension JSONB 키 (없으면 null). */
    public String granularityKey() {
        if (granularity == null) return null;
        return switch (granularity.toLowerCase()) {
            case "daily" -> "date";
            case "weekly" -> "week";
            case "monthly" -> "month";
            case "quarterly" -> "quarter";
            case "yearly" -> "year";
            default -> null;
        };
    }

    /** 페이지네이션 OFFSET. */
    public int offset() {
        return Math.max(0, page) * (size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String normalizeGranularity(String g) {
        return (g == null || g.isBlank()) ? null : g;
    }
}
