package kr.co.ircp.cms.domain.governance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * retention_policy 생성·수정 요청 DTO.
 */
public record RetentionPolicyRequest(
        @NotBlank String targetTable,
        @NotBlank String policyType,
        @Min(1) Integer retentionMonths,
        String archiveTable,
        String anonymizeColumns,
        String scheduleCron,
        String status,
        String description
) {}
