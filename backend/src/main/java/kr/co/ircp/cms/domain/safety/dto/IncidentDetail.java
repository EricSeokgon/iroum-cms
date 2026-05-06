package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;

/**
 * 사고사례 상세 응답.
 * REQ-SAFETY-001: GET /incidents/{id}
 */
public record IncidentDetail(
        Long id,
        String sourceType,
        String industryCode,
        String occupationCode,
        String processType,
        String incidentType,
        Instant occurredAt,
        String severity,
        int casualties,
        String location,
        String summary,
        String detailedCause,
        String preventionLesson,
        String sourceUrl,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
