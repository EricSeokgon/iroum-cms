package kr.co.ircp.cms.domain.notification.admin.dto;

/**
 * WebSocket 연결 확인 메시지.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 §4.2.2, REQ-NWS-004 — 핸드셰이크 완료 직후 서버가
 * 구독자의 {@code /user/queue/notifications/ack} 로 1회 발행하여 현재 미읽음 수를 전달한다.
 *
 * @param type 메시지 타입 식별자(항상 {@code "CONNECTED"})
 * @param unreadCount 연결 시점의 미읽음 총 수
 */
public record ConnectionAckPayload(String type, int unreadCount) {

    /** 메시지 타입 상수. */
    public static final String TYPE_CONNECTED = "CONNECTED";

    public static ConnectionAckPayload of(int unreadCount) {
        return new ConnectionAckPayload(TYPE_CONNECTED, unreadCount);
    }
}
