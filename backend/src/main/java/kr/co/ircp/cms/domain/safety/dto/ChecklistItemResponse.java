package kr.co.ircp.cms.domain.safety.dto;

/** 체크리스트 항목 응답. */
public record ChecklistItemResponse(
        Long id,
        Long templateId,
        String category,
        String itemText,
        String severity,
        int sortOrder,
        String status
) {}
