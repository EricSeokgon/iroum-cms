package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 복구 시험 이력.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011~012: DAR-009 RTO 240분 / RPO 60분 목표 측정 기록.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryDrillLog {

    private Long id;
    private LocalDate drillDate;
    /** BACKUP_RESTORE | FAILOVER | PITR */
    private String drillType;
    /** PASS | FAIL | PARTIAL */
    private String result;
    private Integer rtoActualMin;
    private Integer rpoActualMin;
    private Integer rtoTargetMin;
    private Integer rpoTargetMin;
    private Long performedBy;
    private String checklistJson;
    private String notes;
    private Instant createdAt;
}
