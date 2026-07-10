package kr.co.ircp.cms.domain.notification.admin.websocket;

import kr.co.ircp.cms.domain.notification.admin.dto.NotificationPushPayload;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;
import kr.co.ircp.cms.domain.notification.admin.event.AdminNotificationCreatedEvent;
import kr.co.ircp.cms.domain.notification.admin.repository.AdminNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 신규 관리자 알림 이벤트를 STOMP 개인 큐로 중계하는 발행자.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-002 — {@link AdminNotificationCreatedEvent} 를 구독하여
 * 대상 관리자({@code admin_user_id})의 {@code /user/queue/notifications} 로
 * {@link NotificationPushPayload} 를 즉시 푸시한다.
 *
 * <p>발행 시점은 알림 INSERT 트랜잭션 커밋 직후({@link TransactionPhase#AFTER_COMMIT})로,
 * 클라이언트가 푸시 직후 REST 로 재조회해도 일관된 데이터를 보장한다. 트랜잭션 컨텍스트가 없는
 * 호출(테스트 등)에서도 동작하도록 {@code fallbackExecution = true} 를 사용한다.
 *
 * <p>관리자별 토픽({@code /topic/notifications/{userId}})으로 발행하며, 구독 인가는
 * {@code WebSocketAuthChannelInterceptor} 가 SUBSCRIBE 단계에서 본인 userId 토픽만 허용하여
 * 타 관리자에게 메시지가 노출되지 않도록 격리한다(AC-NWS-002 격리).
 */
// @MX:ANCHOR: [AUTO] AdminNotificationWebSocketPublisher — 알림 생성 → 실시간 푸시의 단일 중계점
// @MX:REASON: AdminNotificationService 이벤트 발행 + SimpMessagingTemplate + Mapper 의존, WS 푸시 경로 전체 영향 (fan_in >= 3)
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotificationWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminNotificationMapper mapper;

    /**
     * REQ-NWS-002 — 알림 생성 이벤트 수신 → 대상 관리자에게 STOMP 푸시.
     *
     * <p>비연결 관리자에게는 Spring 의 user destination resolver 가 메시지를 폐기하므로
     * 별도 연결 여부 검사 없이 항상 전송을 시도한다(버퍼링 불필요).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAdminNotificationCreated(AdminNotificationCreatedEvent event) {
        AdminNotification n = event.notification();
        Long adminUserId = n.getAdminUserId();
        if (adminUserId == null) {
            log.warn("AdminNotificationCreatedEvent 의 adminUserId 가 null — 푸시 생략 (id={})", n.getId());
            return;
        }

        // INSERT 직후 엔티티에는 id 만 채워지고 created_at(DB default) 은 비어 있으므로
        // 저장된 행을 재조회하여 정확한 created_at 을 페이로드에 담는다(AC-NWS-002).
        AdminNotification saved = mapper.findByIdAndUser(n.getId(), adminUserId);
        AdminNotification source = (saved != null) ? saved : n;

        int unreadCount = (int) mapper.countUnread(adminUserId);
        NotificationPushPayload payload = NotificationPushPayload.of(source, unreadCount);

        // 관리자별 토픽으로 발행. 구독 인가(본인 토픽만 허용)는 채널 인터셉터가 강제하므로
        // 비연결 관리자에게는 SimpleBroker 가 구독 부재로 메시지를 폐기한다(버퍼링 불필요).
        messagingTemplate.convertAndSend(WebSocketTopics.notifications(adminUserId), payload);

        log.debug("WS 알림 푸시 완료: adminUserId={}, notificationId={}, unreadCount={}",
                adminUserId, n.getId(), unreadCount);
    }
}
