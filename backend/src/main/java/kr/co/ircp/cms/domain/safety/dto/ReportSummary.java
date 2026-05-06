package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;
import java.util.UUID;

/** 보고서 목록 응답. */
public record ReportSummary(
        Long id,
        UUID uuid,
        Long companyProfileId,
        Long templateId,
        String riskGrade,
        Instant generatedAt,
        int accessedCount
) {}
