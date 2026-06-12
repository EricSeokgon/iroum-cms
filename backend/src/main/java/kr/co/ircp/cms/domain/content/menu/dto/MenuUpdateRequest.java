package kr.co.ircp.cms.domain.content.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 수정 요청 DTO (이름·URL·대상 변경).
 * REQ-CONTENT-001-D-3
 */
public record MenuUpdateRequest(
        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 500)
        String url,

        @NotBlank @Pattern(regexp = "_self|_blank")
        String target
) {}
