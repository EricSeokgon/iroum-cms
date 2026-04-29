package kr.co.ircp.cms.domain.content.page.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 페이지 생성 요청 DTO.
 * REQ-CONTENT-005-D-1: 페이지 생성 (slug 패턴·유일성 검증)
 */
public record PageCreateRequest(
        @NotNull
        Long siteId,

        @NotNull
        Long templateId,

        Long menuId,

        @NotBlank @Size(max = 100)
        String code,

        @NotBlank @Size(max = 300)
        String title,

        /** ^[a-z0-9][a-z0-9\-/]*$ */
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9\\-/]*$")
        @Size(max = 255)
        String slug
) {}
