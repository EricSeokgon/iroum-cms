package kr.co.ircp.cms.domain.search.dto;

import java.time.Instant;

/**
 * 통합 검색 단일 결과 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-001/002 응답 스키마.
 */
public record DocResult(
        String docType,
        Long docId,
        String title,
        String snippet,
        double rank,
        String domain,
        String url,
        Instant createdAt
) {
}
