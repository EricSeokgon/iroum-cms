package kr.co.ircp.cms.domain.policy.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** POST /api/v1/policy/admin/programs 요청. */
public record PolicyProgramCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 50) String ministry,
        @NotBlank @Size(max = 300) String programName,
        String programNameI18n,
        String descriptionHtml,
        List<String> targetIndustries,
        List<String> targetRegions,
        Integer minEmployees,
        Integer maxEmployees,
        Long minRevenue,
        Long maxRevenue,
        Integer minBusinessAgeMonths,
        Integer maxBusinessAgeMonths,
        Instant applicationStart,
        Instant applicationEnd,
        Long budgetTotal,
        Long budgetPerCompany,
        String sourceUrl,
        String status
) {}
