package kr.co.ircp.cms.domain.auth.exception;

/**
 * 인증 시도 횟수 초과 예외 (HTTP 403).
 *
 * <p>REQ-AUTH-017-D-2 — 최대 3회 시도 초과 시 발생. 해당 requestId는 FAILED 처리됨.
 */
public class VerificationAttemptExceededException extends AuthException {

    public VerificationAttemptExceededException() {
        super("VERIFICATION_ATTEMPT_EXCEEDED");
    }
}
