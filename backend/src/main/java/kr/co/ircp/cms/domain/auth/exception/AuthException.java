package kr.co.ircp.cms.domain.auth.exception;

/**
 * 인증·인가 예외 계층의 최상위 클래스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~005 — 모든 Auth 예외는 이 클래스를 상속한다.
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
