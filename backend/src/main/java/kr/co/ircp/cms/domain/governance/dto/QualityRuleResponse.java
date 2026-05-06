package kr.co.ircp.cms.domain.governance.dto;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;

import java.math.BigDecimal;
import java.time.Instant;

public record QualityRuleResponse(
        Long id,
        String targetTable,
        String targetColumn,
        String ruleType,
        BigDecimal threshold,
        BigDecimal rangeMin,
        BigDecimal rangeMax,
        String severity,
        String status,
        String scheduleCron,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static QualityRuleResponse from(DataQualityRule r) {
        return new QualityRuleResponse(
                r.getId(), r.getTargetTable(), r.getTargetColumn(),
                r.getRuleType(), r.getThreshold(), r.getRangeMin(), r.getRangeMax(),
                r.getSeverity(), r.getStatus(), r.getScheduleCron(),
                r.getDescription(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
