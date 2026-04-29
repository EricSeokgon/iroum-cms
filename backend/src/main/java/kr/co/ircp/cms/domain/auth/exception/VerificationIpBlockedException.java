package kr.co.ircp.cms.domain.auth.exception;

/**
 * IP 차단 예외 (HTTP 423).
 *
 * <p>REQ-AUTH-017-D-5 — 동일 IP에서 시간당 10회 초과 시 발생.
 */
public class VerificationIpBlockedException extends AuthException {

    public VerificationIpBlockedException() {
        super("VERIFICATION_IP_BLOCKED");
    }
}
