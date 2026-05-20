package kr.co.ircp.cms.domain.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.co.ircp.cms.domain.audit.entity.AuditLog;

import java.time.Instant;

/**
 * 감사 로그 응답 DTO.
 *
 * <p>프론트엔드 AuditLogResponse 인터페이스 매핑:
 * { id, event_time, actor_id?, action, entity_type?, entity_id?,
 *   severity, result, before?, after?, ip_address?, detail? }
 */
public record AuditLogResponse(
        Long id,
        @JsonProperty("event_time") Instant eventTime,
        @JsonProperty("actor_id") Long actorId,
        @JsonProperty("actor_username") String actorUsername,
        String action,
        @JsonProperty("entity_type") String entityType,
        @JsonProperty("entity_id") String entityId,
        String severity,
        String result,
        @JsonProperty("before") String beforeValue,
        @JsonProperty("after") String afterValue,
        @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("user_agent") String userAgent,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("duration_ms") Integer durationMs
) {
    public static AuditLogResponse from(AuditLog e) {
        return new AuditLogResponse(
                e.getId(),
                e.getEventTime(),
                e.getActorId(),
                null,
                e.getAction(),
                e.getEntityType(),
                e.getEntityId(),
                e.getSeverity(),
                e.getResult(),
                e.getBeforeValue(),
                e.getAfterValue(),
                e.getIpAddress(),
                e.getUserAgent(),
                e.getTraceId(),
                e.getFailureReason(),
                e.getDurationMs()
        );
    }
}
