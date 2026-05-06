package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 대시보드 위젯 정의 엔티티.
 * REQ-VIZ-001
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardWidget {
    private Long id;
    /** 전역 유일 코드 (예: PV_BY_FEATURE) */
    private String code;
    private String name;
    private String description;
    /** METRIC_CARD/LINE_CHART/BAR_CHART/PIE_CHART/RADAR_CHART/MATRIX_HEATMAP/TABLE/PROGRESS_BAR/MAP_KOREA */
    private String widgetType;
    /** KPI_VALUE / CUSTOM_QUERY / EXTERNAL */
    private String dataSource;
    /** {kpi_id} 또는 {query_template_id, params} */
    private String dataSourceConfig;
    /** {width, height, color_palette, refresh_sec} */
    private String defaultConfig;
    /** ['period','feature','industry','region','role'] */
    private List<String> availableDimensions;
    /** ['VIEWER','EDITOR','DEPT_ADMIN','SUPER_ADMIN'] */
    private List<String> requiredRoleCodes;
    /** ACTIVE / DEPRECATED / HIDDEN */
    private String status;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
