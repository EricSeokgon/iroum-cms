package kr.co.ircp.cms.domain.auth.exception;

/**
 * 사용자에게 매핑된 역할 삭제 시도 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — 사용자 매핑이 존재하는 역할은 삭제 불가.
 * HTTP 409 Conflict 매핑.
 */
public class RoleHasUsersException extends RuntimeException {

    public RoleHasUsersException(String roleCode, int userCount) {
        super("역할에 " + userCount + "명의 사용자가 배정되어 있어 삭제할 수 없습니다: " + roleCode);
    }
}
