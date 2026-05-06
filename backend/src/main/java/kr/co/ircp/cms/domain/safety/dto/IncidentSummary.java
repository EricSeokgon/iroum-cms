package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;

/**
 * 사고사례 목록 응답.
 * REQ-SAFETY-001: GET /incidents
 */
public record IncidentSummary(
        Long id,
        String sourceType,
        String industryCode,
        String incidentType,
        String severity,
        Instant occurredAt,
        int casualties,
        String location,
        String summary,
        String status
) {}
