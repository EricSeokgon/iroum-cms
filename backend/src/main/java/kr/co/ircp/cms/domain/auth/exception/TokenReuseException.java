package kr.co.ircp.cms.domain.auth.exception;

/**
 * Refresh Token 재사용 탐지 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-002 — 이미 회수(revoke)된 Refresh Token으로 갱신 시도 시 발생.
 * 토큰 탈취 공격(Token Theft)을 감지하며, 해당 사용자의 모든 세션을 강제 종료한다.
 * HTTP 401 Unauthorized 매핑.
 */
public class TokenReuseException extends AuthException {

    public TokenReuseException() {
        super("TOKEN_REUSE_DETECTED");
    }

    public TokenReuseException(String message) {
        super(message);
    }
}
