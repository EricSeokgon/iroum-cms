package kr.co.ircp.cms.domain.content.page.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 페이지 수정 요청 DTO.
 * REQ-CONTENT-005-D-2: 페이지 수정 (이력 누적)
 * SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005: expectedVersion 낙관적 잠금(누락 시 400).
 */
public record PageUpdateRequest(
        @NotBlank @Size(max = 300)
        String title,

        /** 슬러그 변경 시 seo_redirect 자동 INSERT */
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9\\-/]*$")
        @Size(max = 255)
        String slug,

        Long templateId,

        Long menuId,

        @Size(max = 300) String seoTitle,
        @Size(max = 500) String seoDescription,
        @Size(max = 500) String seoKeywords,
        @Size(max = 500) String ogImageUrl,
        @Size(max = 500) String canonicalUrl,

        /** 변경 사유 (page_history.change_summary) */
        @Size(max = 500) String changeSummary,

        /** 클라이언트가 알고 있는 페이지 current_version. 서버 현재 버전과 다르면 409. 누락 시 400. */
        @NotNull Integer expectedVersion
) {}
