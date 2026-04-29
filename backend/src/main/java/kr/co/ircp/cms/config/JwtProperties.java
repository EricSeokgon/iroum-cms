package kr.co.ircp.cms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 설정 프로퍼티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001/002 — application.yml의 iroum.jwt.* 항목을 바인딩.
 * 운영 환경에서는 반드시 JWT_SECRET 환경변수로 시크릿 키를 재정의해야 한다.
 */
// @MX:WARN: [AUTO] 테스트용 기본 시크릿 키 — 운영에서 환경변수로 반드시 재정의
// @MX:REASON: secret 기본값이 코드에 포함되어 있어 환경변수 미설정 시 보안 취약점 발생
@ConfigurationProperties(prefix = "iroum.jwt")
public record JwtProperties(

    /**
     * JWT 서명 시크릿 키 (최소 256비트 = 32바이트).
     *
     * <p>기본값은 개발/테스트 전용 — 운영 시 JWT_SECRET 환경변수 필수.
     */
    String secret,

    /**
     * Access Token 유효 기간 (기본 15분).
     *
     * <p>application.yml: iroum.jwt.access-token-ttl=PT15M
     */
    Duration accessTokenTtl,

    /**
     * Refresh Token 유효 기간 (기본 7일).
     *
     * <p>application.yml: iroum.jwt.refresh-token-ttl=P7D
     */
    Duration refreshTokenTtl,

    /**
     * JWT issuer 클레임 값.
     *
     * <p>application.yml: iroum.jwt.issuer=iroum-cms
     */
    String issuer
) {}
