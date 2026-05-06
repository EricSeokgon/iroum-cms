package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 내보내기 이력 엔티티.
 * REQ-VIZ-006-D-5
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportHistory {
    private Long id;
    private Long requestorId;
    /** EXCEL / CSV / PDF */
    private String exportType;
    /** {dashboard_id, widget_ids:[...], filter_state:{...}} */
    private String scope;
    private String filePath;
    private Long sizeBytes;
    private Integer rowCount;
    /** PROCESSING / COMPLETED / FAILED / EXPIRED */
    private String status;
    private Integer progressPct;
    private String errorMessage;
    private Instant requestedAt;
    private Instant completedAt;
    private Instant expiresAt;
}
