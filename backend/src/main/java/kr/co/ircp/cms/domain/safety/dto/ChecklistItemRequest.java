package kr.co.ircp.cms.domain.safety.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 체크리스트 항목 등록 요청.
 * REQ-SAFETY-005-D + 004-D
 */
public record ChecklistItemRequest(
        @NotBlank String category,
        @NotBlank String itemText,
        String severity,
        Integer sortOrder
) {}
