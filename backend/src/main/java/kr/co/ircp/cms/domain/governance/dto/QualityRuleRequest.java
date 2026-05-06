package kr.co.ircp.cms.domain.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QualityRuleRequest(
        @NotBlank String targetTable,
        String targetColumn,
        @NotBlank String ruleType,
        @NotNull BigDecimal threshold,
        BigDecimal rangeMin,
        BigDecimal rangeMax,
        String severity,
        String status,
        String scheduleCron,
        String description
) {}
