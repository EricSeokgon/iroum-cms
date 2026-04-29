package kr.co.ircp.cms.domain.auth.exception;

/**
 * 중복 사용자 생성 시도 시 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 — POST /api/v1/users 에서 username 또는 email이
 * 이미 존재하는 경우 HTTP 409를 반환한다.
 */
public class DuplicateUserException extends AuthException {

    public DuplicateUserException(String field, String value) {
        super("이미 사용 중인 " + field + "입니다: " + value);
    }
}
