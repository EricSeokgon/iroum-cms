package kr.co.ircp.cms.domain.policy.program.dto;

import java.time.Instant;
import java.util.List;

/** PUT /api/v1/policy/admin/programs/{id} 요청 (모든 필드 optional). */
public record PolicyProgramUpdateRequest(
        String programName,
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
