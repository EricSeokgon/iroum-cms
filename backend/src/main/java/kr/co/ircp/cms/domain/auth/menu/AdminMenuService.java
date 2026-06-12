package kr.co.ircp.cms.domain.auth.menu;

import java.util.List;

/**
 * 어드민 메뉴 접근 제어 서비스.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — 현재 사용자 권한 집합 기반 접근 가능 메뉴 트리 산출.
 */
public interface AdminMenuService {

    /**
     * 사용자가 접근 가능한 활성 메뉴 트리 조회.
     *
     * <p>접근 가능 판정(OR 의미): 메뉴에 매핑된 권한이 없으면 무제한 노출,
     * 매핑된 권한 중 하나라도 보유하면 접근 가능. 자식이 접근 가능하면 부모도 트리에 포함된다.
     *
     * @param userId 사용자 PK
     * @return 부모-자식 트리 구조의 접근 가능 메뉴 목록 (sort_order 정렬)
     */
    List<AccessibleMenu> findAccessibleMenus(long userId);
}
