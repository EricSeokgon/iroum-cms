package kr.co.ircp.cms.domain.dashboard.kpi.dto;

import java.util.List;
import java.util.Map;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 결과 + 페이지네이션/필터 메타.
 *
 * <p>AC-005: 빈 결과에서도 200 + 빈 items + 적용 filters 메타 반환.
 * <p>AC-019: 1000행 초과 시 items 는 최대 1000행, hasMore=true.
 *
 * @param items      현재 페이지 KPI 행 (최대 1000)
 * @param totalCount 필터 조건 전체 행 수
 * @param hasMore    totalCount 가 반환 행 수를 초과하면 true
 * @param filters    적용된 필터 메타 (kpiCode/dimensionJson/granularity/page/size)
 */
// @MX:NOTE: [AUTO] KpiQueryResult — 조회 결과 봉투(items+meta). 빈 결과/상한 메타 포함
public record KpiQueryResult(
        List<KpiValueResponse> items,
        long totalCount,
        boolean hasMore,
        Map<String, Object> filters
) {
}
