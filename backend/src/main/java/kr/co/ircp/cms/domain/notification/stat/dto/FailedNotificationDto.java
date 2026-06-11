package kr.co.ircp.cms.domain.notification.stat.dto;

/**
 * 미발송/오류 알림 목록 항목 응답.
 *
 * <p>SPEC-CMS-NOTIFICATION-STAT-001 REQ-NS-004 — delivery_status IN ('FAILED','PENDING') 알림.
 */
public record FailedNotificationDto(
        Long id,
        Long userId,
        String type,
        String title,
        String deliveryStatus,
        java.time.Instant createdAt
) {}
