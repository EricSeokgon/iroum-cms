package kr.co.ircp.cms.domain.safety.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 사고사례 신규 등록 요청.
 * REQ-SAFETY-001-D-5
 */
public record IncidentCreateRequest(
        @NotBlank String sourceType,
        @NotBlank String industryCode,
        String occupationCode,
        String processType,
        @NotBlank String incidentType,
        @NotNull Instant occurredAt,
        @NotBlank String severity,
        Integer casualties,
        String location,
        @NotBlank String summary,
        String detailedCause,
        String preventionLesson,
        String sourceUrl
) {}
