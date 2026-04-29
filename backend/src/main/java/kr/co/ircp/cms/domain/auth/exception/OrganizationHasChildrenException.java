package kr.co.ircp.cms.domain.auth.exception;

/**
 * 자식 조직이 존재하는 조직 삭제 시 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 409 Conflict 매핑.
 * 자식 노드를 먼저 이동 또는 삭제해야 함.
 */
public class OrganizationHasChildrenException extends RuntimeException {

    public OrganizationHasChildrenException(long orgId) {
        super("자식 조직이 존재하여 삭제할 수 없습니다: id=" + orgId);
    }
}
