package kr.co.ircp.cms.domain.search.dto;

import kr.co.ircp.cms.domain.search.entity.SearchSynonym;

import java.time.Instant;

/**
 * 동의어 응답 DTO (REQ-SEARCH-009).
 */
public record SynonymResponse(
        Long id,
        String term,
        String synonym,
        String locale,
        String status,
        String description,
        Instant createdAt
) {

    public static SynonymResponse from(SearchSynonym e) {
        return new SynonymResponse(
                e.getId(),
                e.getTerm(),
                e.getSynonym(),
                e.getLocale(),
                e.getStatus(),
                e.getDescription(),
                e.getCreatedAt()
        );
    }
}
