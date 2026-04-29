package kr.co.ircp.cms.domain.auth.exception;

/**
 * 유효하지 않은 verifiedToken 예외 (HTTP 401).
 *
 * <p>REQ-AUTH-017-D-4 — verifiedToken 불일치, 만료, 또는 목적 불일치 시 발생.
 */
public class InvalidVerifiedTokenException extends AuthException {

    public InvalidVerifiedTokenException() {
        super("VERIFICATION_TOKEN_INVALID");
    }
}
