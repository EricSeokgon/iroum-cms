package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 정책사업 매칭 월별 통계.
 *
 * <p>SPEC-CMS-009 REQ-DATA-003: PolicyMatchStatsJob이 SPEC-CMS-007 데이터 집계.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyMatchStatsMonthly {

    /** YYYY-MM */
    private String statMonth;
    private Long policyId;
    private Integer matchCount;
    private Integer applyCount;
    private BigDecimal applyConversionRate;
    private Integer successCount;
    private Instant aggregatedAt;
}
