package kr.co.ircp.cms.domain.notification.admin.websocket;

/**
 * 관리자 알림 WebSocket 토픽 목적지 계약.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 §5.2 — 관리자별 개인 토픽 경로를 한 곳에서 정의한다.
 * 발행자(Publisher)·연결확인(AckListener)·인가(ChannelInterceptor)·프론트가 동일 계약을 공유한다.
 *
 * <ul>
 *   <li>알림 푸시: {@code /topic/notifications/{userId}}</li>
 *   <li>연결 확인(ack): {@code /topic/notifications/{userId}/ack}</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] WebSocketTopics — 관리자별 토픽 경로 단일 정의(발행/인가/프론트 공유)
public final class WebSocketTopics {

    private WebSocketTopics() {
    }

    /** 관리자별 알림 토픽 prefix. 뒤에 userId 가 붙는다. */
    public static final String TOPIC_PREFIX = "/topic/notifications/";

    /** ack 토픽 suffix. */
    public static final String ACK_SUFFIX = "/ack";

    /** 관리자 알림 토픽 목적지. */
    public static String notifications(long adminUserId) {
        return TOPIC_PREFIX + adminUserId;
    }

    /** 관리자 연결 확인(ack) 토픽 목적지. */
    public static String ack(long adminUserId) {
        return TOPIC_PREFIX + adminUserId + ACK_SUFFIX;
    }
}
