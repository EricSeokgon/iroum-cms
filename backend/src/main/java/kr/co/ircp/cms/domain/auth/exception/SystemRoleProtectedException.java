package kr.co.ircp.cms.domain.auth.exception;

/**
 * 시스템 기본 역할 삭제 시도 예외.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — is_system=true 역할은 삭제 불가.
 * HTTP 400 Bad Request 매핑.
 */
public class SystemRoleProtectedException extends RuntimeException {

    public SystemRoleProtectedException(String roleCode) {
        super("시스템 기본 역할은 삭제할 수 없습니다: " + roleCode);
    }
}
