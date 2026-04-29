package kr.co.ircp.cms.domain.auth.exception;

/**
 * 비밀번호 재사용 금지 위반 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-010 — 직전 5회 사용한 비밀번호를 재사용하려 할 때 발생.
 * HTTP 400 Bad Request, 코드 PASSWORD_REUSE 반환.
 */
public class PasswordReuseException extends AuthException {

    public PasswordReuseException(String message) {
        super(message);
    }
}
