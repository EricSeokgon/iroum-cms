package kr.co.ircp.cms.domain.search.dto;

/**
 * 인기 검색어 응답 항목 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-006.
 */
public record PopularQueryItem(
        String query,
        long count,
        int rank
) {
}
