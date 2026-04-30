package kr.co.ircp.cms.domain.system.stats.dto;

import java.util.List;

/**
 * 방문자 통계 응답 DTO.
 *
 * <p>REQ-SYSTEM-002-D — GET /api/v1/system/stats/visitors
 */
public record VisitorStatsResponse(
        List<TrendItemResponse> trends,
        Long totalVisits,
        Long totalUniqueVisitors
) {}
