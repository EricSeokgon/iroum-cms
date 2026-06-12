package kr.co.ircp.cms.domain.auth.menu;

/**
 * admin_menu 메타 행 DTO (권한 매핑 제외).
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — 메뉴 카탈로그 단순 조회용.
 *
 * @param menuKey   메뉴 고유키
 * @param name      표시명
 * @param parentKey 상위 메뉴 키 (NULL=최상위)
 * @param routePath Vue 라우트 경로 (그룹 메뉴는 NULL)
 * @param sortOrder 정렬 순서
 * @param icon      아이콘 식별자
 */
public record AdminMenuMeta(
        String menuKey,
        String name,
        String parentKey,
        String routePath,
        int sortOrder,
        String icon
) {}
