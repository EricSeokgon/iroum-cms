package kr.co.ircp.cms.domain.search.dto;

/**
 * 통합 검색 요청 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001/004.
 *
 * @param query  검색어 (≤ 200자)
 * @param domain ALL/board/content/policy/safety/media/publication (default ALL)
 * @param page   1-base 페이지 번호 (default 1)
 * @param size   페이지 크기 (1..50, default 20)
 * @param locale ko | en (default ko)
 */
public record SearchRequest(
        String query,
        String domain,
        int page,
        int size,
        String locale
) {
}
