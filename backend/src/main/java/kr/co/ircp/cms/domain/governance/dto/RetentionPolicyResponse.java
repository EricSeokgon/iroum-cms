package kr.co.ircp.cms.domain.governance.dto;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;

import java.time.Instant;

public record RetentionPolicyResponse(
        Long id,
        String targetTable,
        String policyType,
        Integer retentionMonths,
        String archiveTable,
        String anonymizeColumns,
        String scheduleCron,
        String status,
        String description,
        Long updatedBy,
        Instant updatedAt
) {

    public static RetentionPolicyResponse from(RetentionPolicy p) {
        return new RetentionPolicyResponse(
                p.getId(), p.getTargetTable(), p.getPolicyType(), p.getRetentionMonths(),
                p.getArchiveTable(), p.getAnonymizeColumns(),
                p.getScheduleCron(), p.getStatus(), p.getDescription(),
                p.getUpdatedBy(), p.getUpdatedAt());
    }
}
