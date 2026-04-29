package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * JWT 토큰 생성·검증 서비스 인터페이스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001/002/003 — jjwt 0.12.6 기반 구현 (Step 2 GREEN에서).
 */
// @MX:ANCHOR: [AUTO] JwtTokenProvider — Access/Refresh Token 생명주기의 핵심 계약
// @MX:REASON: AuthService, AuthController, SecurityFilter 등 fan_in >= 3 참조
public interface JwtTokenProvider {

    /**
     * Access Token 생성.
     *
     * @param userId 사용자 ID
     * @param username 로그인 ID
     * @param roles 부여된 역할 코드 집합
     * @return JWT 문자열 (유효기간 15분)
     */
    String generateAccessToken(long userId, String username, Set<String> roles);

    /**
     * Refresh Token 생성.
     *
     * <p>userId만 포함하는 최소한의 클레임으로 생성 (권한 클레임 제외).
     *
     * @param userId 사용자 ID
     * @return JWT 문자열 (유효기간 7일)
     */
    String generateRefreshToken(long userId);

    /**
     * Access Token 검증 및 클레임 추출.
     *
     * @param token JWT 문자열
     * @return 클레임 (유효한 경우), empty (서명 불일치 등)
     * @throws TokenExpiredException 토큰 만료 시
     */
    Optional<JwtClaims> validateAccessToken(String token);

    /**
     * Refresh Token에서 userId 추출.
     *
     * @param refreshToken JWT 문자열
     * @return userId (유효한 경우), empty (파싱 불가)
     */
    Optional<Long> extractUserId(String refreshToken);

    /**
     * Access Token 파싱 결과 클레임.
     *
     * @param userId 사용자 ID
     * @param username 로그인 ID
     * @param roles 역할 코드 집합
     * @param expiresAt 만료 시각
     */
    record JwtClaims(long userId, String username, Set<String> roles, Instant expiresAt) {}
}
