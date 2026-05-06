package kr.co.ircp.cms.domain.policy.program.dto;

import java.time.Instant;
import java.util.List;

/** 정책사업 상세 응답. */
public record PolicyProgramDetail(
        Long id,
        String code,
        String ministry,
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
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
