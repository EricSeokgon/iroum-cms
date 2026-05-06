package kr.co.ircp.cms.domain.policy.matching.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** PUT /api/v1/policy/company-profile 요청. */
public record CompanyProfileUpsertRequest(
        @NotNull Long companyId,
        List<String> industryCodes,
        List<String> regionCodes,
        Integer employeeCount,
        Long annualRevenue,
        Integer businessAgeMonths,
        List<String> certifications,
        String customAttrs
) {}
