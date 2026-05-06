package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;
import java.util.UUID;

/** 보고서 단건 상세 응답. */
public record ReportDetail(
        Long id,
        UUID uuid,
        Long companyProfileId,
        Long templateId,
        String riskGrade,
        String matchedIncidentsJsonb,
        String contentHtml,
        String contentPdfPath,
        Instant generatedAt,
        int accessedCount
) {}
