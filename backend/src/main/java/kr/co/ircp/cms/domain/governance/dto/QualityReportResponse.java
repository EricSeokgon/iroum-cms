package kr.co.ircp.cms.domain.governance.dto;

import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;

import java.math.BigDecimal;
import java.time.Instant;

public record QualityReportResponse(
        Long id,
        Long ruleId,
        Instant checkedAt,
        BigDecimal measuredValue,
        Boolean violation,
        String detail,
        Boolean notified
) {

    public static QualityReportResponse from(DataQualityReport r) {
        return new QualityReportResponse(
                r.getId(), r.getRuleId(), r.getCheckedAt(),
                r.getMeasuredValue(), r.getViolation(),
                r.getDetail(), r.getNotified());
    }
}
