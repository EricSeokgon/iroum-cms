package kr.co.ircp.cms.domain.content.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 생성·수정 요청 DTO.
 * REQ-CONTENT-001-D-1: 메뉴 생성
 */
public record MenuRequest(
        @NotNull
        Long siteId,

        Long parentId,

        @NotBlank @Size(max = 100)
        String code,

        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 500)
        String url,

        @NotBlank @Pattern(regexp = "_self|_blank")
        String target,

        @Size(max = 100)
        String icon,

        int sortOrder,

        boolean isVisible,

        String metadata
) {}
