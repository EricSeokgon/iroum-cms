package kr.co.ircp.cms.domain.auth.entity;

/**
 * 권한 변경 유형 열거형.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016-D-1 — permission_change_history.change_type 컬럼 값 집합.
 */
public enum PermissionChangeType {

    /** 사용자에게 역할 부여 */
    ROLE_ASSIGN,

    /** 사용자에서 역할 회수 */
    ROLE_UNASSIGN,

    /** 역할에 권한 부여 */
    ROLE_PERMISSION_GRANT,

    /** 역할에서 권한 회수 */
    ROLE_PERMISSION_REVOKE
}
