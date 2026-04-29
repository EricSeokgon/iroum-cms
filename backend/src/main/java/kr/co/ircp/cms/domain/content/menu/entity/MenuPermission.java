package kr.co.ircp.cms.domain.content.menu.entity;

/**
 * 메뉴-권한 매핑 엔티티.
 * REQ-CONTENT-002-D: 메뉴별 권한 매핑 (SPEC-CMS-002 menu_permissions 재사용)
 */
@lombok.Data
@lombok.Builder
public class MenuPermission {

    /** 메뉴 ID (FK → menu.id) */
    private Long menuId;
    /** 권한 코드 (FK → permissions.code) */
    private String permissionCode;
}
