package kr.co.ircp.cms.domain.search.dto;

import java.util.List;
import java.util.Map;

/**
 * 검색 통계 응답 (운영자용, REQ-SEARCH-008).
 *
 * @param topQueries       상위 쿼리 (query/count)
 * @param zeroResultRatio  0건 검색 비율
 * @param avgResponseMs    평균 응답 시간(ms)
 * @param totalSearches    총 검색 수
 */
public record SearchStatsResponse(
        List<Map<String, Object>> topQueries,
        double zeroResultRatio,
        double avgResponseMs,
        long totalSearches
) {}
