package kr.co.ircp.cms.domain.governance.quality;

import java.math.BigDecimal;

/**
 * 품질 룰 측정 결과.
 *
 * <p>SPEC-CMS-009 REQ-DATA-007 — 5종 룰의 공통 결과 타입.
 *
 * @param measuredValue 측정값 (NULL_RATIO/IQR/UNIQUE는 0~1 비율, FRESHNESS는 시간(h))
 * @param violation     임계값 위반 여부
 * @param detail        상세 설명 (rule_type, threshold, measured, 사용 SQL 등)
 */
public record QualityCheckResult(
        BigDecimal measuredValue,
        boolean violation,
        String detail
) {

    /**
     * ERROR 결과 빌더 — checker 내부에서 catch한 예외를 violation=true로 변환.
     */
    public static QualityCheckResult error(String message) {
        return new QualityCheckResult(null, true, "ERROR: " + message);
    }
}
