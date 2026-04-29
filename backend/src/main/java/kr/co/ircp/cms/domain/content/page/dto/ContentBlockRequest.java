package kr.co.ircp.cms.domain.content.page.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 콘텐츠 블록 생성·수정 요청 DTO.
 * REQ-CONTENT-006-D-1: 블록 추가/수정
 */
public record ContentBlockRequest(
        @NotBlank @Pattern(regexp = "RICH_TEXT|IMAGE|HTML|MARKDOWN|EMBED")
        String blockType,

        int sortOrder,

        /** JSON 페이로드 (block_type별 스키마) */
        @NotBlank
        String payload
) {}
