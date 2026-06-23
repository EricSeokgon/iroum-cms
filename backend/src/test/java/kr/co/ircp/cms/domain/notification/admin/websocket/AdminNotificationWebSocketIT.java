package kr.co.ircp.cms.domain.notification.admin.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.notification.admin.dto.ConnectionAckPayload;
import kr.co.ircp.cms.domain.notification.admin.dto.NotificationPushPayload;
import kr.co.ircp.cms.domain.notification.admin.entity.AdminNotification;
import kr.co.ircp.cms.domain.notification.admin.service.AdminNotificationService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * SPEC-CMS-NOTIFICATION-WS-001: WebSocket 실시간 알림 푸시 통합 테스트.
 *
 * <p>커버 AC:
 * AC-NWS-001(연결·인증·CONNECTED ack), AC-NWS-002(신규 알림 즉시 수신 + 사용자 격리),
 * AC-NWS-001(만료 토큰 거부).
 *
 * <p>실제 내장 서버에 STOMP-over-WebSocket 으로 연결하여 핸드셰이크 JWT 인증과
 * 알림 INSERT → STOMP 푸시 흐름을 검증한다.
 */
// @MX:NOTE: [AUTO] AdminNotificationWebSocketIT — SPEC-CMS-NOTIFICATION-WS-001 WS 핸드셰이크/푸시/격리 통합 검증
// @MX:SPEC: SPEC-CMS-NOTIFICATION-WS-001
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("AdminNotification WebSocket IT (SPEC-CMS-NOTIFICATION-WS-001)")
class AdminNotificationWebSocketIT extends AbstractIntegrationTest {

    @LocalServerPort int port;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminNotificationService notificationService;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long otherAdminId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("ws-admin-" + suffix);
        otherAdminId = insertUser("ws-other-" + suffix);
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
    }

    @AfterEach
    void cleanup() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // ─── AC-NWS-001: 연결·인증·CONNECTED ack ────────────────────────────────

    @Test
    @DisplayName("AC-NWS-001: 유효한 ADMIN JWT 로 연결 → CONNECTED ack(unreadCount) 수신")
    void connect_withValidAdminToken_receivesConnectedAck() throws Exception {
        // 미읽음 2건 사전 생성
        insertNotification(adminId, "POST_APPROVAL_REQUEST", "INFO", "사전알림1 " + suffix);
        insertNotification(adminId, "SECURITY_EVENT", "WARN", "사전알림2 " + suffix);
        stubToken(adminId, Set.of("ADMIN"));

        StompSession session = connect(adminId);
        CompletableFuture<Object> ackFuture = new CompletableFuture<>();
        session.subscribe(ackTopic(adminId),
                payloadHandler(ConnectionAckPayload.class, ackFuture));

        Object ack = ackFuture.get(5, TimeUnit.SECONDS);
        assertThat(ack).isInstanceOf(ConnectionAckPayload.class);
        ConnectionAckPayload payload = (ConnectionAckPayload) ack;
        assertThat(payload.type()).isEqualTo("CONNECTED");
        assertThat(payload.unreadCount()).isEqualTo(2);

        session.disconnect();
    }

    // ─── AC-NWS-002: 신규 알림 즉시 수신 ────────────────────────────────────

    @Test
    @DisplayName("AC-NWS-002: 구독 중 INSERT → 관리자 토픽으로 NOTIFICATION 수신")
    void insert_pushesNotificationToSubscribedAdmin() throws Exception {
        stubToken(adminId, Set.of("ADMIN"));

        StompSession session = connect(adminId);
        CompletableFuture<Object> msgFuture = new CompletableFuture<>();
        session.subscribe(notificationsTopic(adminId),
                payloadHandler(NotificationPushPayload.class, msgFuture));

        // 약간의 지연 후 알림 생성 (구독 등록 완료 보장)
        Thread.sleep(300);
        AdminNotification n = AdminNotification.builder()
                .adminUserId(adminId)
                .type("POST_APPROVAL_REQUEST")
                .severity("INFO")
                .title("실시간 알림 " + suffix)
                .refType("POST")
                .refId(456L)
                .build();
        notificationService.insert(n);

        Object received = msgFuture.get(5, TimeUnit.SECONDS);
        assertThat(received).isInstanceOf(NotificationPushPayload.class);
        NotificationPushPayload p = (NotificationPushPayload) received;
        assertThat(p.type()).isEqualTo("NOTIFICATION");
        assertThat(p.notificationType()).isEqualTo("POST_APPROVAL_REQUEST");
        assertThat(p.severity()).isEqualTo("INFO");
        assertThat(p.title()).isEqualTo("실시간 알림 " + suffix);
        assertThat(p.refType()).isEqualTo("POST");
        assertThat(p.refId()).isEqualTo(456L);
        assertThat(p.createdAt()).isNotNull();
        assertThat(p.unreadCount()).isGreaterThanOrEqualTo(1);

        session.disconnect();
    }

    @Test
    @DisplayName("AC-NWS-002: 관리자 B 알림 INSERT 는 관리자 A 큐에 전달되지 않음(격리)")
    void insert_forOtherAdmin_notDeliveredToThisAdmin() throws Exception {
        stubToken(adminId, Set.of("ADMIN"));

        StompSession session = connect(adminId);
        CompletableFuture<Object> msgFuture = new CompletableFuture<>();
        session.subscribe(notificationsTopic(adminId),
                payloadHandler(NotificationPushPayload.class, msgFuture));

        Thread.sleep(300);
        // 타 관리자(otherAdminId) 알림 생성
        notificationService.insert(AdminNotification.builder()
                .adminUserId(otherAdminId)
                .type("SECURITY_EVENT")
                .severity("WARN")
                .title("타인 실시간 알림 " + suffix)
                .build());

        // adminId 큐에는 도착하지 않아야 함 → 타임아웃 시 정상
        assertThat(msgFuture).isNotCompleted();
        try {
            msgFuture.get(1, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.fail("관리자 A 큐에 타 관리자 알림이 전달되면 안 됨");
        } catch (java.util.concurrent.TimeoutException expected) {
            // 정상: 격리됨
        }

        session.disconnect();
    }

    // ─── AC-NWS-001: 만료 토큰 거부 ─────────────────────────────────────────

    @Test
    @DisplayName("AC-NWS-001: 유효하지 않은 JWT 로 연결 → 핸드셰이크/CONNECT 거부")
    void connect_withInvalidToken_isRejected() {
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.empty());

        WebSocketStompClient client = stompClient();
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer invalid-token");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> {
            CompletableFuture<StompSession> fut = client.connectAsync(
                    wsUrl(), new org.springframework.web.socket.WebSocketHttpHeaders(),
                    headers, new StompSessionHandlerAdapter() {});
            fut.get(5, TimeUnit.SECONDS);
        });
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    private StompSession connect(long userId) throws Exception {
        WebSocketStompClient client = stompClient();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer test-ws-token-" + userId);
        return client.connectAsync(
                wsUrl(), new org.springframework.web.socket.WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        // NotificationPushPayload.createdAt(Instant) 역직렬화를 위해 JavaTimeModule 등록.
        converter.getObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        client.setMessageConverter(converter);
        return client;
    }

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws/notifications";
    }

    private String notificationsTopic(long userId) {
        return "/topic/notifications/" + userId;
    }

    private String ackTopic(long userId) {
        return "/topic/notifications/" + userId + "/ack";
    }

    private <T> StompSessionHandlerAdapter payloadHandler(Class<T> type, CompletableFuture<Object> future) {
        return new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload != null) {
                    future.complete(payload);
                }
            }
        };
    }

    private void stubToken(long userId, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "ws-" + userId, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', 'WS테스트관리자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertNotification(long userId, String type, String severity, String title) {
        jdbcTemplate.update(
                "INSERT INTO admin_notification (admin_user_id, type, severity, title, status, " +
                "created_at) VALUES (?, ?, ?, ?, 'UNREAD', NOW())",
                userId, type, severity, title);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_notification WHERE admin_user_id = ? AND title = ? " +
                "ORDER BY id DESC LIMIT 1",
                Long.class, userId, title);
        return id == null ? -1L : id;
    }
}
