package kr.co.ircp.cms.domain.auth.exception;

/**
 * 가입 이메일 인증 토큰 무효 예외 (HTTP 403).
 *
 * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 — {@code REGISTRATION_EMAIL_VERIFY_REQUIRED=true}
 * 이고 {@code verifiedToken} 이 존재하나 만료(5분 초과)·목적 불일치(purpose != SIGNUP)·미검증인 경우 발생한다.
 */
public class RegistrationTokenInvalidException extends AuthException {

    public RegistrationTokenInvalidException() {
        super("REGISTRATION_VERIFY_TOKEN_INVALID");
    }
}
