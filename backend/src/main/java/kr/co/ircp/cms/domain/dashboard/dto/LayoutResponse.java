package kr.co.ircp.cms.domain.dashboard.dto;

import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayout;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayoutWidget;

import java.time.Instant;
import java.util.List;

/**
 * 레이아웃 상세 응답 DTO.
 * REQ-VIZ-002
 */
public record LayoutResponse(
        Long id,
        Long ownerId,
        String name,
        String description,
        boolean isDefault,
        String gridConfig,
        List<String> sharedWith,
        List<WidgetMapping> widgets,
        Instant createdAt,
        Instant updatedAt
) {
    public record WidgetMapping(
            Long widgetId,
            String instanceId,
            String position,
            String configOverride,
            int sortOrder
    ) {
        public static WidgetMapping from(DashboardLayoutWidget m) {
            return new WidgetMapping(
                    m.getWidgetId(), m.getInstanceId(),
                    m.getPosition(), m.getConfigOverride(), m.getSortOrder());
        }
    }

    public static LayoutResponse from(DashboardLayout l, List<DashboardLayoutWidget> widgets) {
        return new LayoutResponse(
                l.getId(), l.getOwnerId(), l.getName(), l.getDescription(),
                l.isDefault(), l.getGridConfig(), l.getSharedWith(),
                widgets.stream().map(WidgetMapping::from).toList(),
                l.getCreatedAt(), l.getUpdatedAt());
    }
}
