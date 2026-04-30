package kr.co.ircp.cms.domain.system.stats.dto;

import java.time.LocalDate;

/**
 * 추이 시계열 항목 DTO.
 *
 * <p>REQ-SYSTEM-002-D — 30일 일별 방문/페이지뷰/오류 추이
 */
public record TrendItemResponse(
        LocalDate date,
        Integer visits,
        Integer pageViews,
        Integer errors
) {}
