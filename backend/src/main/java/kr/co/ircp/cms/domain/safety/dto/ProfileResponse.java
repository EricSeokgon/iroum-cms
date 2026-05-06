package kr.co.ircp.cms.domain.safety.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 기업 안전 프로필 응답. */
public record ProfileResponse(
        Long id,
        Long companyId,
        String industryCode,
        String subIndustry,
        Integer employeeCount,
        String primaryProcess,
        List<String> hazardFactors,
        BigDecimal riskScore,
        String riskGrade,
        Instant updatedAt
) {}
