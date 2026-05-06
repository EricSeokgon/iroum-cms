package kr.co.ircp.cms.domain.dashboard.dto;

import kr.co.ircp.cms.domain.dashboard.entity.DashboardWidget;

import java.time.Instant;
import java.util.List;

/**
 * 위젯 상세 응답 DTO.
 * REQ-VIZ-001
 */
public record WidgetResponse(
        Long id,
        String code,
        String name,
        String description,
        String widgetType,
        String dataSource,
        String dataSourceConfig,
        String defaultConfig,
        List<String> availableDimensions,
        List<String> requiredRoleCodes,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static WidgetResponse from(DashboardWidget w) {
        return new WidgetResponse(
                w.getId(), w.getCode(), w.getName(), w.getDescription(),
                w.getWidgetType(), w.getDataSource(), w.getDataSourceConfig(),
                w.getDefaultConfig(), w.getAvailableDimensions(),
                w.getRequiredRoleCodes(), w.getStatus(),
                w.getCreatedAt(), w.getUpdatedAt());
    }
}
