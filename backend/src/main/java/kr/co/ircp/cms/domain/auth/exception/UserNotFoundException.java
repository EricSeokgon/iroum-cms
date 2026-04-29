package kr.co.ircp.cms.domain.auth.exception;

/**
 * 사용자를 찾을 수 없는 경우 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 — GET/PUT/DELETE /api/v1/users/{id} 에서
 * 대상 사용자가 존재하지 않거나 소프트 삭제된 경우 HTTP 404를 반환한다.
 */
public class UserNotFoundException extends AuthException {

    public UserNotFoundException(long id) {
        super("사용자를 찾을 수 없습니다: id=" + id);
    }
}
