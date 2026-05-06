package kr.co.ircp.cms.domain.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 위젯 등록/수정 요청 DTO.
 * REQ-VIZ-001-D-1
 */
public record WidgetRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        String description,
        @NotBlank String widgetType,
        @NotBlank String dataSource,
        @NotNull String dataSourceConfig,
        String defaultConfig,
        List<String> availableDimensions,
        List<String> requiredRoleCodes,
        String status
) {
}
