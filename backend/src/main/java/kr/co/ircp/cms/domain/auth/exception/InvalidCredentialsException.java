package kr.co.ircp.cms.domain.auth.exception;

/**
 * 잘못된 자격증명 예외 (사용자 미존재 또는 비밀번호 불일치).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001 — Enumeration 방지를 위해 단일 에러 코드 반환.
 * HTTP 401 Unauthorized 매핑.
 */
public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        super("AUTH_INVALID_CREDENTIALS");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
