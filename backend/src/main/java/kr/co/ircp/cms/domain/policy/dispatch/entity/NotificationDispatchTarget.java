package kr.co.ircp.cms.domain.policy.dispatch.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 발송 대상 엔티티.
 * REQ-POLICY-003-D-2 멱등성: idempotency_key UNIQUE.
 * SPEC-CMS-007 §4.2.8
 */
@Data
@Builder
public class NotificationDispatchTarget {
    private Long id;
    private Long scheduleId;
    private Long userId;
    /** notification_send.id — logical FK (SPEC-CMS-004) */
    private Long sendId;
    /** SHA-256 hash(schedule_id || user_id || dispatch_type) */
    private String idempotencyKey;
    /** KAKAO / EMAIL / SMS / INAPP */
    private String channel;
    /** PENDING / SENT / FAILED / SKIPPED_OPTOUT / CANCELLED */
    private String status;
    private Instant evaluatedAt;
    private Instant sentAt;
    private String failedReason;
}
