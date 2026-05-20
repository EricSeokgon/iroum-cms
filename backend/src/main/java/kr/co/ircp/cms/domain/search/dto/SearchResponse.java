package kr.co.ircp.cms.domain.search.dto;

import java.util.List;
import java.util.Map;

/**
 * 통합 검색 응답 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001/004/009.
 */
public record SearchResponse(
        Long searchLogId,
        int totalElements,
        int totalPages,
        List<DocResult> content,
        Map<String, Long> byDomainFacets,
        String expandedQuery
) {
}
