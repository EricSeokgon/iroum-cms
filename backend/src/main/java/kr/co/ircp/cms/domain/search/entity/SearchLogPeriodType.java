package kr.co.ircp.cms.domain.search.entity;

/**
 * 인기 검색어 캐시 period_type enum.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006/007: 일/주/월 단위 인기 검색어 집계.
 */
public enum SearchLogPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY
}
