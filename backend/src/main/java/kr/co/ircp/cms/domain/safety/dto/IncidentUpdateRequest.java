package kr.co.ircp.cms.domain.safety.dto;

import java.time.Instant;

/**
 * 사고사례 수정 요청 (REQ-SAFETY-001-D-5).
 */
public record IncidentUpdateRequest(
        String industryCode,
        String occupationCode,
        String processType,
        String incidentType,
        Instant occurredAt,
        String severity,
        Integer casualties,
        String location,
        String summary,
        String detailedCause,
        String preventionLesson,
        String status
) {}
