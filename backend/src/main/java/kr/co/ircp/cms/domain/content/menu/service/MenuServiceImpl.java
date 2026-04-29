package kr.co.ircp.cms.domain.content.menu.service;

import kr.co.ircp.cms.domain.content.menu.dto.MenuMoveRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuOrderRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuResponse;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;
import kr.co.ircp.cms.domain.content.menu.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 메뉴 서비스 구현체.
 * REQ-CONTENT-001-D / REQ-CONTENT-002-D: 메뉴 트리 관리
 *
 * // @MX:NOTE: [AUTO] RED 단계 골격. Step 2 GREEN에서 실제 구현.
 * // @MX:TODO: [AUTO] Step 2 GREEN에서 UnsupportedOperationException 제거 후 실제 로직 채움
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;

    @Override
    @Transactional
    public MenuResponse createMenu(MenuRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public List<MenuTreeNode> getMenuTree(Long siteId) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    public List<MenuTreeNode> getMenuTreeForUser(Long siteId, Set<String> userPermissions) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public MenuResponse changeOrder(Long id, MenuOrderRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public MenuResponse moveMenu(Long id, MenuMoveRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public MenuResponse toggleVisibility(Long id, boolean isVisible) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }

    @Override
    @Transactional
    public void replaceMenuPermissions(Long menuId, MenuPermissionRequest request) {
        throw new UnsupportedOperationException("RED: not yet implemented");
    }
}
