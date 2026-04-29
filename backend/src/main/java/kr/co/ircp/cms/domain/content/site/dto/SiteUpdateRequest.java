package kr.co.ircp.cms.domain.content.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사이트 수정 요청 DTO.
 * REQ-CONTENT-003-D: 사이트 정보 수정
 */
public record SiteUpdateRequest(
        @NotBlank @Size(max = 200)
        String name,

        @NotBlank @Size(max = 255)
        String domain,

        @NotBlank @Size(max = 10)
        String defaultLanguage,

        String metadata
) {}
