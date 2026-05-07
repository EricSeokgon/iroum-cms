package kr.co.ircp.cms.domain.search.dto;

/**
 * 자동완성 결과 항목 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-005.
 *
 * @param term       제안어
 * @param similarity 0..1 유사도 점수
 * @param source     popular | content
 */
public record AutocompleteItem(
        String term,
        double similarity,
        String source
) {
}
