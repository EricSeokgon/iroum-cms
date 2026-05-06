package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;
import java.util.List;

/** 가이드라인 템플릿 응답. */
public record TemplateResponse(
        Long id,
        String code,
        String name,
        String description,
        List<String> applicableIndustryCodes,
        List<String> applicableGrades,
        String structure,
        String status,
        String version,
        String reviewStatus,
        Instant createdAt
) {}
