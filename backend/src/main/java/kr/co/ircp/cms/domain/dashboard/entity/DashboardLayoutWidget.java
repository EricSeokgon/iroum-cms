package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 레이아웃-위젯 매핑 엔티티.
 * REQ-VIZ-002-D-1 (12-grid 배치)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutWidget {
    private Long layoutId;
    private Long widgetId;
    private String instanceId;
    /** {x, y, w, h} */
    private String position;
    private String configOverride;
    private int sortOrder;
}
