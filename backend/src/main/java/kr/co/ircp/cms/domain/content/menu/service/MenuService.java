package kr.co.ircp.cms.domain.content.menu.service;

import kr.co.ircp.cms.domain.content.menu.dto.MenuMoveRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuOrderRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuResponse;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;

import java.util.List;
import java.util.Set;

/**
 * 메뉴 서비스 인터페이스.
 * REQ-CONTENT-001-D: 메뉴 트리 CRUD + 이동 + 권한 매핑
 * REQ-CONTENT-002-D: 메뉴-권한 매핑
 *
 * // @MX:ANCHOR: [AUTO] MenuService — 메뉴 비즈니스 계약
 * // @MX:REASON: MenuController에서 fan_in >= 3으로 참조
 * // @MX:SPEC: REQ-CONTENT-001-D, REQ-CONTENT-002-D
 */
public interface MenuService {

    /** 메뉴 생성 (depth/code 유일성 검증 포함) */
    MenuResponse createMenu(MenuRequest request);

    /** 메뉴 트리 조회 (path, sort_order 정렬 + children 중첩) */
    List<MenuTreeNode> getMenuTree(Long siteId);

    /**
     * 사용자 권한 기반 메뉴 트리 조회.
     * 권한 없는 메뉴는 accessible=false로 표시.
     */
    List<MenuTreeNode> getMenuTreeForUser(Long siteId, Set<String> userPermissions);

    /** 메뉴 순서 변경 */
    MenuResponse changeOrder(Long id, MenuOrderRequest request);

    /**
     * 메뉴 이동 (새 parent 지정).
     * 순환 참조/depth 초과 시 예외 발생.
     */
    MenuResponse moveMenu(Long id, MenuMoveRequest request);

    /** 메뉴 가시성 토글 */
    MenuResponse toggleVisibility(Long id, boolean isVisible);

    /** 메뉴 삭제 (자손 CASCADE) */
    void deleteMenu(Long id);

    /** 메뉴-권한 일괄 저장(replace) */
    void replaceMenuPermissions(Long menuId, MenuPermissionRequest request);
}
