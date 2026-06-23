package kr.co.ircp.cms.domain.notification.admin.event;

import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;

/**
 * 신규 관리자 알림 생성 이벤트.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-002 — {@code AdminNotificationService.insert()} 가
 * 알림을 저장한 직후 {@code ApplicationEventPublisher} 로 발행한다.
 * {@code AdminNotificationWebSocketPublisher} 가 이를 구독하여 STOMP 푸시로 중계한다.
 *
 * <p>이벤트 페이로드로 저장된 엔티티 전체를 담아 리스너가 매핑에 필요한 모든 필드를 갖는다.
 *
 * @param notification 저장 완료된(id 채워진) 알림 엔티티
 */
public record AdminNotificationCreatedEvent(AdminNotification notification) {

    /** 알림 수신 대상 관리자 ID (STOMP user destination 키). */
    public Long adminUserId() {
        return notification.getAdminUserId();
    }
}
