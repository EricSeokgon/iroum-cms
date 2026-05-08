package kr.co.ircp.cms.domain.auth.exception;

/**
 * admin 사용자 검색 email partial 패턴 입력 거부 예외.
 *
 * <p>REQ-PII-EMAIL-007 — email 컬럼은 HMAC 완전일치 검색만 허용한다.
 * partial 패턴(*, %, _, @ 미포함 등) 입력 시 400 Bad Request 반환.
 */
public class AdminEmailPartialSearchException extends AuthException {

    /** REQ-PII-EMAIL-007 에러 코드. */
    public static final String CODE = "ADMIN_EMAIL_PARTIAL_FORBIDDEN";

    public AdminEmailPartialSearchException() {
        super("email 컬럼은 완전일치 검색만 허용됩니다");
    }

    public AdminEmailPartialSearchException(String message) {
        super(message);
    }
}
