package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 데이터 품질 룰 엔티티.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~007: NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS 5종 룰.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityRule {

    private Long id;
    private String targetTable;
    private String targetColumn;
    /** NULL_RATIO | RANGE | IQR | UNIQUE | FRESHNESS */
    private String ruleType;
    private BigDecimal threshold;
    private BigDecimal rangeMin;
    private BigDecimal rangeMax;
    /** INFO | WARN | CRITICAL */
    private String severity;
    /** ACTIVE | PAUSED */
    private String status;
    private String scheduleCron;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
