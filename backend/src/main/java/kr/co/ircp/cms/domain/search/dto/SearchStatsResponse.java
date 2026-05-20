package kr.co.ircp.cms.domain.search.dto;

import java.util.List;
import java.util.Map;

/**
 * 검색 통계 응답 (운영자용, REQ-SEARCH-008).
 *
 * @param from          조회 시작일 (ISO-8601 yyyy-MM-dd)
 * @param to            조회 종료일 (ISO-8601 yyyy-MM-dd)
 * @param totalSearches 총 검색 수
 * @param uniqueQueries 고유 검색어 수
 * @param topQueries    상위 쿼리 (query/searchCount/clickCount/ctr)
 */
public record SearchStatsResponse(
        String from,
        String to,
        long totalSearches,
        long uniqueQueries,
        List<Map<String, Object>> topQueries
) {}
