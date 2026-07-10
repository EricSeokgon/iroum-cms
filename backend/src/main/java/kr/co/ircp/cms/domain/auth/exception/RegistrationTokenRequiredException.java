package kr.co.ircp.cms.domain.auth.exception;

/**
 * 가입 이메일 인증 토큰 누락 예외 (HTTP 400).
 *
 * <p>SPEC-CMS-USER-APPROVAL-002 REQ-UA2-002 — {@code REGISTRATION_EMAIL_VERIFY_REQUIRED=true}
 * 인데 가입 요청에 {@code verifiedToken} 필드 자체가 누락된 경우 발생한다.
 */
public class RegistrationTokenRequiredException extends AuthException {

    public RegistrationTokenRequiredException() {
        super("REGISTRATION_VERIFY_TOKEN_REQUIRED");
    }
}
