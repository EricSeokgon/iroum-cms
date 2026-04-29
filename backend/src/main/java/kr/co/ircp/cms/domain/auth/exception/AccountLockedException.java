package kr.co.ircp.cms.domain.auth.exception;

/**
 * 계정 잠금 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-005 — 5회 실패 시 30분 잠금.
 * HTTP 423 Locked 매핑.
 */
public class AccountLockedException extends AuthException {

    public AccountLockedException() {
        super("AUTH_ACCOUNT_LOCKED");
    }

    public AccountLockedException(String message) {
        super(message);
    }
}
