package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.LoginResponse;
import kr.co.ircp.cms.domain.auth.dto.RefreshResult;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
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
     * 일반 로그인 (ID/비밀번호).
     *
     * <p>REQ-AUTH-001 — 자격증명 검증, JWT 발급, 실패 횟수 관리, 이력 기록.
     * 성공 시 login_history, refresh_tokens 삽입 및 fail_count 리셋.
     *
     * @param req 로그인 요청
     * @param ipAddress 클라이언트 IP
     * @param userAgent 클라이언트 User-Agent
     * @return Access Token 정보 (Refresh Token은 Set-Cookie로 별도 처리)
     * @throws InvalidCredentialsException 사용자 미존재 또는 비밀번호 불일치
     * @throws AccountLockedException 계정 잠금 상태
     */
    LoginResponse login(LoginRequest req, String ipAddress, String userAgent)
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
}
