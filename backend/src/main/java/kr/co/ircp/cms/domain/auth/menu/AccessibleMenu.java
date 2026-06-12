package kr.co.ircp.cms.domain.auth.menu;

import java.util.List;

/**
 * 접근 가능 어드민 메뉴 트리 노드 응답 DTO.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — GET /api/v1/admin/menus/accessible 응답.
 * 부모-자식 트리 구조. 자식이 접근 가능하면 부모도 함께 포함된다.
 *
 * @param menuKey   메뉴 고유키
 * @param name      표시명
 * @param routePath Vue 라우트 경로 (그룹 메뉴는 NULL)
 * @param icon      아이콘 식별자
 * @param sortOrder 정렬 순서
 * @param children  하위 접근 가능 메뉴 목록 (없으면 빈 리스트)
 */
public record AccessibleMenu(
        String menuKey,
        String name,
        String routePath,
        String icon,
        int sortOrder,
        List<AccessibleMenu> children
) {}
