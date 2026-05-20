package kr.co.ircp.cms.domain.system.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * 방문자 통계 일별 응답 DTO.
 * 프론트엔드 VisitorStatsResponse: { date, count, unique, page_views }
 *
 * <p>REQ-SYSTEM-002-D — GET /api/v1/system/stats/visitors
 */
public record VisitorStatsResponse(
        LocalDate date,
        int count,
        int unique,
        @JsonProperty("page_views") int pageViews
) {
    public static VisitorStatsResponse from(TrendItemResponse t) {
        int visits = t.getVisits() != null ? t.getVisits() : 0;
        return new VisitorStatsResponse(
                t.getDate(),
                visits,
                visits,
                t.getPageViews() != null ? t.getPageViews() : 0
        );
    }
}
