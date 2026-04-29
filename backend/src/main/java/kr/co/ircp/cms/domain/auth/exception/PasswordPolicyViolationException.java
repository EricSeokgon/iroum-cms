package kr.co.ircp.cms.domain.auth.exception;

/**
 * 비밀번호 정책 위반 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004 — 비밀번호가 정책(8자 이상, 3종류 이상 문자)을 충족하지 않을 때 발생.
 * HTTP 400 Bad Request 매핑.
 */
public class PasswordPolicyViolationException extends AuthException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
