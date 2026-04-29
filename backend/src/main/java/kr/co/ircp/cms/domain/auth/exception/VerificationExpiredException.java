package kr.co.ircp.cms.domain.auth.exception;

/**
 * 인증 요청 만료 예외 (HTTP 403).
 *
 * <p>REQ-AUTH-017-D-2 — expires_at 초과 후 confirm 시도 시 발생.
 */
public class VerificationExpiredException extends AuthException {

    public VerificationExpiredException() {
        super("VERIFICATION_EXPIRED");
    }
}
