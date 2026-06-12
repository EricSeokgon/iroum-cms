package kr.co.ircp.cms.domain.auth.menu;

/**
 * admin_menu_permissions 매핑 행 DTO.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — 메뉴↔권한 매핑 행(OR 의미).
 *
 * @param menuKey        메뉴 고유키
 * @param permissionCode 권한 코드
 */
public record AdminMenuPermissionRow(
        String menuKey,
        String permissionCode
) {}
