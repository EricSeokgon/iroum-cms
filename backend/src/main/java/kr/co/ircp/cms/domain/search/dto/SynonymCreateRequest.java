package kr.co.ircp.cms.domain.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 동의어 등록 요청 DTO.
 *
 * <p>SPEC-CMS-010 REQ-SEARCH-009.
 */
public record SynonymCreateRequest(
        @NotBlank @Size(max = 100) String term,
        @NotBlank @Size(max = 100) String synonym,
        @NotBlank @Size(max = 10) String locale,
        String description
) {
}
