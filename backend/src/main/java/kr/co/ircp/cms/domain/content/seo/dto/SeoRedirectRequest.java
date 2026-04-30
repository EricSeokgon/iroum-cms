package kr.co.ircp.cms.domain.content.seo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * SEO 리다이렉트 생성 요청 DTO.
 * REQ-CONTENT-005-D-8: from_path → to_path, 301/302
 */
public record SeoRedirectRequest(
        @NotBlank String fromPath,
        @NotBlank String toPath,
        Short httpStatus,
        String reason
) {}
