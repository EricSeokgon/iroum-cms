package kr.co.ircp.cms.domain.search.dto;

import java.util.List;

/**
 * 인기 검색어 응답 (REQ-SEARCH-006).
 *
 * @param period      DAILY|WEEKLY|MONTHLY
 * @param periodDate  해당 기간 시작일 (ISO-8601)
 * @param items       Top-N 항목
 */
public record PopularResponse(String period, String periodDate, List<PopularQueryItem> items) {}
