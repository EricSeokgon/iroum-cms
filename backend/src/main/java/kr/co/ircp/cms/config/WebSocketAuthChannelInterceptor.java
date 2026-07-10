package kr.co.ircp.cms.config;

import java.util.Optional;
import java.util.Set;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 핸드셰이크 단계의 JWT 인증 인터셉터.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-001/006 — CONNECT 프레임의
 * {@code Authorization: Bearer <JWT>} 헤더를 추출하여 기존 {@link JwtTokenProvider} 로 검증한다.
 *
 * <ul>
 *   <li>토큰 누락·만료·서명 오류 → 인증 예외로 핸드셰이크 거부(HTTP 401)</li>
 *   <li>관리자 역할(SUPER_ADMIN/CONTENT_ADMIN/ADMIN) 아님 → 접근 거부(HTTP 403)</li>
 *   <li>검증 성공 → {@link StompHeaderAccessor#setUser(java.security.Principal)} 로
 *       {@link JwtPrincipal} 주입 → 개인 큐 라우팅(/user/{userId}/queue) 가능</li>
 * </ul>
 *
 * <p>기존 REST 의 {@code JwtAuthenticationFilter} 와 동일한 검증 로직(블랙리스트·클레임 추출)을
 * 재사용하여 인증 정책 일관성을 보장한다.
 */
// @MX:ANCHOR: [AUTO] WebSocketAuthChannelInterceptor — WS 인증 진입점. 모든 STOMP 세션 수립이 경유
// @MX:REASON: WebSocketConfig.configureClientInboundChannel 등록 + JwtTokenProvider/TokenBlacklistMapper 의존, 인증 흐름 전체 영향 (fan_in >= 3)
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    /** REQ-NWS-001 — WS 연결 허용 관리자 역할(REST 가드와 동일). */
    private static final Set<String> ALLOWED_ROLES =
            Set.of("SUPER_ADMIN", "CONTENT_ADMIN", "ADMIN");

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistMapper tokenBlacklistMapper;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            // REQ-NWS-006 격리 — 본인 userId 토픽만 구독 허용(타 관리자 토픽 구독 차단)
            authorizeSubscription(accessor);
            return message;
        }

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            // CONNECT/SUBSCRIBE 외 프레임은 세션에 이미 바인딩된 Principal 을 신뢰
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // REQ-NWS-001 — 토큰 누락 시 핸드셰이크 거부(401)
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "WebSocket 인증 토큰이 없습니다");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // 로그아웃(블랙리스트) 토큰 거부 — REST 필터와 동일 정책
        if (tokenBlacklistMapper.exists(HashUtil.sha256Hex(token))) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "로그아웃된 토큰입니다");
        }

        Optional<JwtTokenProvider.JwtClaims> claimsOpt = jwtTokenProvider.validateAccessToken(token);
        if (claimsOpt.isEmpty()) {
            // REQ-NWS-001/006 — 만료·서명 오류 → 401
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "유효하지 않은 토큰입니다");
        }

        JwtTokenProvider.JwtClaims claims = claimsOpt.get();
        boolean hasAdminRole = claims.roles().stream().anyMatch(ALLOWED_ROLES::contains);
        if (!hasAdminRole) {
            // REQ-NWS-001 — 권한 부족 → 403
            throw new org.springframework.security.access.AccessDeniedException(
                    "WebSocket 접근 권한이 없습니다");
        }

        // 세션 Principal 주입 — getName()=userId 로 고정하여 SUBSCRIBE 인가·ack 발행 시 일관 사용.
        accessor.setUser(new StompUserPrincipal(claims.userId(), claims.username()));

        log.debug("WebSocket STOMP CONNECT 인증 완료: userId={}", claims.userId());
        return message;
    }

    /**
     * REQ-NWS-006 격리 — SUBSCRIBE 목적지가 본인 userId 토픽인지 검증한다.
     *
     * <p>관리자별 토픽 {@code /topic/notifications/{userId}}(및 {@code .../ack})만 허용하며,
     * 타 관리자 userId 토픽 구독 시도는 거부한다. 비-알림 토픽 구독은 통과시킨다(본 SPEC 범위 외).
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(NOTIFICATION_TOPIC_PREFIX)) {
            return;
        }
        java.security.Principal user = accessor.getUser();
        if (user == null) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "WebSocket 세션 인증 정보가 없습니다");
        }
        // /topic/notifications/{userId} 또는 /topic/notifications/{userId}/ack
        String rest = destination.substring(NOTIFICATION_TOPIC_PREFIX.length());
        String userIdPart = rest.endsWith("/ack")
                ? rest.substring(0, rest.length() - "/ack".length())
                : rest;
        if (!userIdPart.equals(user.getName())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "타 관리자 알림 토픽 구독은 허용되지 않습니다");
        }
    }

    /** 관리자별 알림 토픽 prefix(WebSocketTopics 와 동일 계약). */
    private static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    /**
     * STOMP 세션 라우팅 키를 userId 로 고정하는 Principal.
     *
     * <p>{@code getName()} 이 userId 문자열을 반환하여 SUBSCRIBE 인가·ack 발행이 일관되게 동작한다.
     */
    record StompUserPrincipal(long userId, String username) implements java.security.Principal {
        @Override
        public String getName() {
            return String.valueOf(userId);
        }
    }
}
