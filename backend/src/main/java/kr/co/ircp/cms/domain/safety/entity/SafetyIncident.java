package kr.co.ircp.cms.domain.safety.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 사고사례 마스터 엔티티.
 * REQ-SAFETY-001-D: 외부 사고 데이터 수집·관리
 */
@Data
@Builder
public class SafetyIncident {
    private Long id;
    private String sourceType;        // DISASTER_WHITE_BOOK / KOSHA_OPENAPI / MOEL_STAT / MANUAL
    private String industryCode;      // KSIC 5자리
    private String occupationCode;
    private String processType;
    private String incidentType;      // FALL / TRAP / COLLISION / ...
    private Instant occurredAt;
    private String severity;          // FATAL / SEVERE / MINOR / MATERIAL
    private int casualties;
    private String location;
    private String summary;
    private String detailedCause;
    private String preventionLesson;
    private String sourceUrl;
    private String status;            // DRAFT / PUBLISHED / ARCHIVED
    private Instant createdAt;
    private Instant updatedAt;
}
