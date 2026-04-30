package kr.co.ircp.cms.domain.system.stats.dto;

/**
 * 인기 페이지 응답 DTO.
 *
 * <p>REQ-SYSTEM-002-D — 기간별 Top 10 페이지 조회
 */
public record TopPageResponse(
        String pageUrl,
        Long count,
        Integer rank
) {}
