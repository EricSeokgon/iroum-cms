package kr.co.ircp.cms.domain.notification.admin.dto;

import java.time.Instant;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;

/**
 * WebSocket 으로 푸시되는 실시간 알림 메시지.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 §4.2.1 — 서버가 신규 admin_notification INSERT 직후
 * 해당 관리자의 {@code /user/queue/notifications} 큐로 발행한다. 스키마 변경 없이
 * 기존 {@link AdminNotification} 엔티티를 그대로 매핑한다.
 *
 * @param type 메시지 타입 식별자(항상 {@code "NOTIFICATION"})
 * @param id admin_notification.id
 * @param notificationType admin_notification.type
 * @param severity INFO / WARN / ERROR
 * @param title 알림 제목
 * @param refType 딥링크 리소스 타입(nullable)
 * @param refId 딥링크 리소스 ID(nullable)
 * @param createdAt 생성 시각(ISO-8601 UTC)
 * @param unreadCount 현재 미읽음 총 수(배지용)
 */
// @MX:NOTE: [AUTO] NotificationPushPayload — WS 푸시 계약. 프론트 useNotificationWs 와 JSON 형식 일치 필요
public record NotificationPushPayload(
        String type,
        Long id,
        String notificationType,
        String severity,
        String title,
        String refType,
        Long refId,
        Instant createdAt,
        int unreadCount) {

    /** 메시지 타입 상수 — 클라이언트 분기 키. */
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";

    /**
     * 저장된 알림 엔티티와 현재 미읽음 수로 푸시 페이로드를 구성한다.
     */
    public static NotificationPushPayload of(AdminNotification n, int unreadCount) {
        return new NotificationPushPayload(
                TYPE_NOTIFICATION,
                n.getId(),
                n.getType(),
                n.getSeverity(),
                n.getTitle(),
                n.getRefType(),
                n.getRefId(),
                n.getCreatedAt(),
                unreadCount);
    }
}
