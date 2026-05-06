package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 데이터 보존·이관 정책 엔티티.
 *
 * <p>SPEC-CMS-009 REQ-GOV-006~009: target_table별 DELETE/ARCHIVE/ANONYMIZE 정책 자동화.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicy {

    private Long id;
    private String targetTable;
    /** DELETE | ARCHIVE | ANONYMIZE */
    private String policyType;
    private Integer retentionMonths;
    private String archiveTable;
    /** JSONB ["email","phone"] — ANONYMIZE 정책에 사용 */
    private String anonymizeColumns;
    private String scheduleCron;
    /** ACTIVE | PAUSED */
    private String status;
    private String description;
    private Long updatedBy;
    private Instant updatedAt;
}
