package kr.co.ircp.cms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-001 — {@code /ws/notifications} 엔드포인트와
 * 개인 큐(/user) 기반 메시지 브로커를 구성한다. SockJS 폴백을 활성화하여
 * WebSocket 미지원 환경에서도 동일 경로로 연결을 수립한다.
 *
 * <ul>
 *   <li>STOMP 엔드포인트: {@code /ws/notifications} (+ SockJS)</li>
 *   <li>브로커 prefix: {@code /topic}(미사용 브로드캐스트), {@code /user}(개인 큐)</li>
 *   <li>앱 목적지 prefix: {@code /app}</li>
 *   <li>user 목적지 prefix: {@code /user}</li>
 * </ul>
 *
 * <p>핸드셰이크 단계의 JWT 인증은 {@link WebSocketSecurityConfig} 의 ChannelInterceptor 가 담당한다.
 */
// @MX:ANCHOR: [AUTO] WebSocketConfig — STOMP 엔드포인트·브로커 진입점. 변경 시 모든 WS 구독 경로 영향
// @MX:REASON: 프론트 useNotificationWs, AdminNotificationWebSocketPublisher, 보안 인터셉터가 본 설정의 prefix 계약에 의존 (fan_in >= 3)
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** SPEC-CMS-NOTIFICATION-WS-001 §5.1 — WebSocket/SockJS 핸드셰이크 엔드포인트. */
    public static final String WS_ENDPOINT = "/ws/notifications";

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    /**
     * CORS 허용 Origin 목록(콤마 구분). SecurityConfig 의 CORS 설정과 동일 기본값을 사용한다.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${iroum.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String corsAllowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        // 순수 WebSocket 경로
        registry.addEndpoint(WS_ENDPOINT)
                .setAllowedOrigins(origins);
        // SockJS 폴백 경로(REQ-NWS-001) — 동일 엔드포인트
        registry.addEndpoint(WS_ENDPOINT)
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 인메모리 Simple Broker — 단일 인스턴스 기준(SPEC §8.4)
        registry.enableSimpleBroker("/topic", "/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT 프레임에서 JWT 검증 + Principal 주입(REQ-NWS-001/006)
        registration.interceptors(authChannelInterceptor);
    }
}
