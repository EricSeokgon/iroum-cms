package kr.co.ircp.cms.domain.auth.exception;

/**
 * DEPT_ADMIN이 자기 부서·자손 범위 외 리소스에 접근 시도할 때 발생하는 예외.
 *
 * <p>SPEC-CMS-002 Q-24 — DEPT_ADMIN 권한 범위 제한 (자기 부서·자손).
 * HTTP 403 Forbidden 매핑.
 */
public class AccessOutOfScopeException extends RuntimeException {

    public AccessOutOfScopeException(String resourceType, long resourceId) {
        super("접근 권한 범위를 벗어난 리소스입니다: " + resourceType + " id=" + resourceId);
    }

    public AccessOutOfScopeException(String message) {
        super(message);
    }
}
