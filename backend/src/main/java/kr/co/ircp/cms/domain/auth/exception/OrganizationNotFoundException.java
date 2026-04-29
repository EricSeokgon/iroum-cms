package kr.co.ircp.cms.domain.auth.exception;

/**
 * 조직이 존재하지 않거나 소프트 삭제된 경우 발생하는 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-014 — HTTP 404 Not Found 매핑.
 */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(long id) {
        super("조직을 찾을 수 없습니다: id=" + id);
    }

    public OrganizationNotFoundException(String message) {
        super(message);
    }
}
