package kr.co.ircp.cms.domain.auth.exception;

/**
 * 인증 쿨다운 예외 (HTTP 429).
 *
 * <p>REQ-AUTH-017-D-1 — 동일 대상에 1분 이내 재요청 시 발생.
 * Retry-After 헤더 값을 함께 전달한다.
 */
public class VerificationCooldownException extends AuthException {

    /** 재시도 가능까지 남은 초 */
    private final long retryAfterSeconds;

    public VerificationCooldownException(long retryAfterSeconds) {
        super("VERIFICATION_COOLDOWN");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
