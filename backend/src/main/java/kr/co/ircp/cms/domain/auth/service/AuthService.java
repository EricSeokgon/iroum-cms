package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import kr.co.ircp.cms.domain.auth.exception.PasswordReuseException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.exception.TokenReuseException;

/**
 * 인증 서비스 인터페이스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003, 011 — 로그인·토큰 갱신·로그아웃 핵심 흐름.
 */
// @MX:ANCHOR: [AUTO] AuthService — 인증 흐름 전체의 진입점 인터페이스
// @MX:REASON: Controller, SecurityFilter, 관리자 서비스 등 fan_in >= 3 예상
public interface AuthService {

    /**
     * 로그인 결과 — Access Token 응답 바디와 Set-Cookie용 Refresh Token을 함께 반환.
     *
     * <p>REQ-AUTH-001 — Controller가 LoginResponse는 바디로, refreshToken은 HttpOnly Cookie로 분리 처리.
     */
    record LoginOutcome(LoginResponse response, String refreshToken) {}

    /**
     * 일반 로그인 (ID/비밀번호).
     *
     * <p>REQ-AUTH-001 — 자격증명 검증, JWT 발급, 실패 횟수 관리, 이력 기록.
     * 성공 시 login_history, refresh_tokens 삽입 및 fail_count 리셋.
     *
     * @param req 로그인 요청
     * @param ipAddress 클라이언트 IP
     * @param userAgent 클라이언트 User-Agent
     * @return Access Token + Refresh Token (Rotation 후 새 값)
     * @throws InvalidCredentialsException 사용자 미존재 또는 비밀번호 불일치
     * @throws AccountLockedException 계정 잠금 상태
     */
    LoginOutcome login(LoginRequest req, String ipAddress, String userAgent)
            throws InvalidCredentialsException, AccountLockedException;

    /**
     * Refresh Token으로 Access Token 갱신 (Rotation).
     *
     * <p>REQ-AUTH-002 — 기존 Refresh Token 회수 후 새 토큰 쌍 발급.
     * 재사용된 토큰 감지 시 해당 사용자 세션 전체 강제 종료.
     *
     * @param refreshTokenCookie Cookie에서 읽은 Refresh Token 값
     * @param ipAddress 클라이언트 IP
     * @param userAgent 클라이언트 User-Agent
     * @return 새 Access Token + 새 Refresh Token
     * @throws TokenExpiredException Refresh Token 만료
     * @throws TokenReuseException 이미 회수된 토큰 재사용 감지
     */
    RefreshResult refresh(String refreshTokenCookie, String ipAddress, String userAgent)
            throws TokenExpiredException, TokenReuseException;

    /**
     * 로그아웃.
     *
     * <p>REQ-AUTH-003 — Access Token을 블랙리스트에 등록하고 Refresh Token을 회수.
     *
     * @param accessToken 현재 Access Token (Authorization 헤더)
     * @param refreshTokenCookie Cookie의 Refresh Token 값
     */
    void logout(String accessToken, String refreshTokenCookie);

    /**
     * 비밀번호 변경.
     *
     * <p>REQ-AUTH-009 — 현재 비밀번호 확인 후 새 비밀번호로 변경.
     * REQ-AUTH-010 — 직전 5회 사용한 비밀번호 재사용 금지.
     * 변경 성공 시 모든 Refresh Token을 무효화하여 재로그인을 강제한다.
     *
     * @param userId          변경 대상 사용자 PK (JwtPrincipal에서 추출)
     * @param currentPassword 현재 비밀번호 (본인 확인)
     * @param newPassword     변경할 새 비밀번호
     * @throws InvalidCredentialsException    현재 비밀번호 불일치
     * @throws PasswordPolicyViolationException 새 비밀번호 정책 위반
     * @throws PasswordReuseException         직전 5개 비밀번호 재사용
     */
    void changePassword(long userId, String currentPassword, String newPassword)
            throws InvalidCredentialsException, PasswordPolicyViolationException, PasswordReuseException;
}
