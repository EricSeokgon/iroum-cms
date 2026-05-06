package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 안전사고 월별 통계.
 *
 * <p>SPEC-CMS-009 REQ-DATA-004: SafetyStatsMonthlyJob이 SPEC-CMS-006 safety_incidents 집계.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyStatsMonthly {

    /** YYYY-MM */
    private String statMonth;
    private String incidentCategory;
    private Integer incidentCount;
    private Integer casualtyCount;
    private BigDecimal severityAvg;
    private Instant aggregatedAt;
}
