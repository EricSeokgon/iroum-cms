package kr.co.ircp.cms.domain.notification.admin.websocket;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import kr.co.ircp.cms.domain.notification.admin.dto.ConnectionAckPayload;
import kr.co.ircp.cms.domain.notification.admin.repository.AdminNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * STOMP 연결 확인(CONNECTED) 메시지 발행 리스너.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-004 — 클라이언트가 ack 토픽
 * ({@code /topic/notifications/{userId}/ack})을 구독하는 시점에 현재 미읽음 수를 담은
 * {@link ConnectionAckPayload} 를 1회 발행한다. 구독 등록 경합을 피하기 위해 짧은 지연 후 발행한다.
 */
// @MX:NOTE: [AUTO] AdminNotificationAckListener — 구독 시점 CONNECTED ack 발행(REQ-NWS-004)
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotificationAckListener {

    /** ack 토픽 suffix. 실제 경로: {@code /topic/notifications/{userId}/ack}. */
    public static final String ACK_SUFFIX = "/ack";

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminNotificationMapper mapper;
    private final TaskScheduler taskScheduler;

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();
        if (destination == null || user == null) {
            return;
        }
        // ack 토픽 구독에만 반응: /topic/notifications/{userId}/ack
        if (!destination.startsWith(WebSocketTopics.TOPIC_PREFIX)
                || !destination.endsWith(ACK_SUFFIX)) {
            return;
        }

        final long adminUserId;
        try {
            adminUserId = Long.parseLong(user.getName());
        } catch (NumberFormatException e) {
            log.warn("ack 발행 실패 — Principal name 이 userId 가 아님: {}", user.getName());
            return;
        }
        final String ackDestination = destination;

        // SessionSubscribeEvent 는 브로커의 구독 등록과 경합할 수 있어, 즉시 발행 시 메시지가
        // 등록 전 도착하여 폐기된다. 짧은 지연 후 발행하여 구독 등록 완료를 보장한다(SPEC §8.4 100ms 허용).
        taskScheduler.schedule(() -> {
            int unreadCount = (int) mapper.countUnread(adminUserId);
            messagingTemplate.convertAndSend(ackDestination, ConnectionAckPayload.of(unreadCount));
            log.debug("WS CONNECTED ack 발행: userId={}, unreadCount={}", adminUserId, unreadCount);
        }, Instant.now().plus(100, ChronoUnit.MILLIS));
    }
}
