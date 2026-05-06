package kr.co.ircp.cms.domain.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * kpi_value 테이블의 단일 행 (SPEC-CMS-005 의존).
 *
 * <p>SPEC-CMS-008 의 위젯 데이터 페치(REQ-VIZ-005-D-1)에서 차트 시리즈 변환에 사용된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiValueRow {
    private Long id;
    private Long kpiId;
    /** dimension JSONB (period, feature, industry, ...) — JSON 텍스트 그대로 */
    private String dimension;
    private BigDecimal valueNumeric;
    private String valueText;
    private Instant calculatedAt;
}
