package kr.co.ircp.cms.domain.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 레이아웃 등록/수정 요청 DTO.
 * REQ-VIZ-002-D-1, 002-D-2
 */
public record LayoutRequest(
        @NotBlank @Size(max = 128) String name,
        String description,
        String gridConfig,
        List<String> sharedWith,
        List<LayoutWidgetEntry> widgets
) {
    public record LayoutWidgetEntry(
            Long widgetId,
            String instanceId,
            String position,
            String configOverride,
            int sortOrder
    ) {}
}
