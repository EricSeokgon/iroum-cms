package kr.co.ircp.cms.domain.search.dto;

/**
 * 검색 결과 클릭 추적 요청 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-008: POST /api/v1/search/click body.
 */
public record ClickRequest(
        Long searchLogId,
        String docType,
        Long docId,
        Integer rank
) {
}
