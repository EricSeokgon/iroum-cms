package kr.co.ircp.cms.config;

import org.springframework.context.annotation.Configuration;

/**
 * WebSocket 보안 설정 마커.
 *
 * <p>SPEC-CMS-NOTIFICATION-WS-001 REQ-NWS-001/006 — WebSocket 메시지 채널 인증은
 * {@link WebSocketAuthChannelInterceptor}(STOMP CONNECT 단계 JWT 검증)가 담당한다.
 *
 * <p>Spring Security 6.x 에서 {@code AbstractSecurityWebSocketMessageBrokerConfigurer} 는
 * deprecated 되었으므로, 본 프로젝트는 ChannelInterceptor 기반 인증을 채택한다.
 * 핸드셰이크 HTTP 업그레이드 경로({@code /ws/notifications/**})는
 * {@link SecurityConfig} 에서 permitAll 로 열어두고, 실제 인증/인가는 STOMP CONNECT 프레임에서
 * 토큰 검증으로 수행한다(토큰 누락·만료 → 401, 권한 부족 → 403).
 *
 * <p>이 클래스는 보안 정책의 위치를 명시하는 문서화 목적의 빈으로 유지한다.
 */
// @MX:NOTE: [AUTO] WebSocketSecurityConfig — WS 인증은 ChannelInterceptor 위임. 정책 위치 문서화
@Configuration
public class WebSocketSecurityConfig {
    // 인증 로직은 WebSocketAuthChannelInterceptor 에 위치한다.
}
