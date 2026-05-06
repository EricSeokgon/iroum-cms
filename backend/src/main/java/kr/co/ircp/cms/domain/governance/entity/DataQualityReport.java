package kr.co.ircp.cms.domain.governance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 데이터 품질 검사 리포트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-007~008: 룰 실행 결과 + 위반 시 알림 트리거.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataQualityReport {

    private Long id;
    private Long ruleId;
    private Instant checkedAt;
    private BigDecimal measuredValue;
    private Boolean violation;
    private String detail;
    private Boolean notified;
}
