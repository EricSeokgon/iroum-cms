package kr.co.ircp.cms.domain.safety.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 기업 안전 프로필 upsert 요청.
 * REQ-SAFETY-002-D-1
 */
public record ProfileUpsertRequest(
        @NotBlank String industryCode,
        String subIndustry,
        Integer employeeCount,
        String primaryProcess,
        List<String> hazardFactors,
        String riskGrade
) {}
