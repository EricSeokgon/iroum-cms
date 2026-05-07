package kr.co.ircp.cms.domain.search.dto;

import jakarta.validation.constraints.Size;

/**
 * 동의어 수정 요청 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009: synonym 본문 또는 status(ACTIVE/PAUSED)를 부분 갱신.
 */
public record SynonymUpdateRequest(
        @Size(max = 100) String synonym,
        @Size(max = 20) String status
) {
}
