package kr.co.ircp.cms.domain.auth.exception;

/**
 * 소속 사용자가 있는 조직 삭제 시 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 409 Conflict 매핑.
 * 사용자 조직 이동 후 삭제해야 함.
 */
public class OrganizationHasUsersException extends RuntimeException {

    public OrganizationHasUsersException(long orgId) {
        super("소속 사용자가 있어 조직을 삭제할 수 없습니다: id=" + orgId);
    }
}
