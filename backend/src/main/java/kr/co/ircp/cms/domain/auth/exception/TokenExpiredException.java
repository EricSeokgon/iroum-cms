package kr.co.ircp.cms.domain.auth.exception;

/**
 * 토큰 만료 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-002/003 — 만료된 Access/Refresh Token 사용 시도.
 * HTTP 401 Unauthorized 매핑.
 */
public class TokenExpiredException extends AuthException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED");
    }

    public TokenExpiredException(String message) {
        super(message);
    }
}
