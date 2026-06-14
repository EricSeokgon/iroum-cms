package kr.co.ircp.cms.domain.content.menu.service;

import kr.co.ircp.cms.domain.content.menu.dto.MenuMoveRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuOrderRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuResponse;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;
import kr.co.ircp.cms.domain.content.menu.dto.MenuUpdateRequest;
import kr.co.ircp.cms.domain.content.menu.entity.Menu;
import kr.co.ircp.cms.domain.content.menu.entity.MenuPermission;
import kr.co.ircp.cms.domain.content.menu.exception.MenuCircularReferenceException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuCodeDuplicateException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuDepthExceededException;
import kr.co.ircp.cms.domain.content.menu.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 메뉴 서비스 구현체.
 * REQ-CONTENT-001-D / REQ-CONTENT-002-D: 메뉴 트리 관리
 *
 * // @MX:ANCHOR: [AUTO] MenuServiceImpl — 메뉴 트리 전체 관리 서비스
 * // @MX:REASON: MenuController, AdminController, SitemapService에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuServiceImpl implements MenuService {

    /** 메뉴 최대 depth (루트=1, 최대=5) */
    private static final short MAX_DEPTH = 5;

    private final MenuMapper menuMapper;

    /**
     * 메뉴 생성.
     * REQ-CONTENT-001-D-1: depth 검증, code 유일성, path/depth 자동 계산
     */
    @Override
    @Transactional
    @CacheEvict(value = "menuTree", allEntries = true)
    public MenuResponse createMenu(MenuRequest request) {
        // 코드 유일성 검증
        if (menuMapper.existsBySiteIdAndCode(request.siteId(), request.code())) {
            throw new MenuCodeDuplicateException(request.siteId(), request.code());
        }

        short newDepth = 1;
        String newPath;

        if (request.parentId() != null) {
            Menu parent = menuMapper.findById(request.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("부모 메뉴를 찾을 수 없습니다. id=" + request.parentId()));
            newDepth = (short) (parent.getDepth() + 1);
            // depth 5 초과 불가 (루트=1, 최대=5이므로 newDepth > 5 이면 거부)
            if (newDepth > MAX_DEPTH) {
                throw new MenuDepthExceededException(newDepth);
            }
            // path는 부모 path + /{새 메뉴 id} 형식이지만 INSERT 전이라 id 미확정 → 임시 placeholder 사용
            newPath = parent.getPath(); // INSERT 후 갱신
        } else {
            newPath = "/"; // 루트 임시값, INSERT 후 갱신
        }

        Menu menu = Menu.builder()
                .siteId(request.siteId())
                .parentId(request.parentId())
                .code(request.code())
                .name(request.name())
                .url(request.url())
                .target(request.target())
                .icon(request.icon())
                .sortOrder(request.sortOrder())
                .depth(newDepth)
                .path(newPath)
                .isVisible(request.isVisible())
                .status("ACTIVE")
                .metadata(request.metadata())
                .build();

        menuMapper.insert(menu);

        // INSERT 후 id 확정 → path 업데이트 (/{parentPath}/{id} 형식)
        String finalPath = (request.parentId() != null)
                ? menuMapper.findById(request.parentId()).map(p -> p.getPath() + "/" + menu.getId()).orElse("/" + menu.getId())
                : "/" + menu.getId();
        menu.setPath(finalPath);
        menuMapper.update(menu);

        return MenuResponse.from(menu);
    }

    /**
     * 사이트 전체 메뉴 트리 조회 (공개용, 권한 필터 없음).
     * REQ-CONTENT-001-D-2
     */
    @Override
    @Cacheable(value = "menuTree", key = "#siteId")
    public List<MenuTreeNode> getMenuTree(Long siteId) {
        List<Menu> menus = menuMapper.findBySiteId(siteId);
        return buildTree(menus, null, true);
    }

    /**
     * 사용자 권한 기반 메뉴 트리 조회.
     * REQ-CONTENT-002-D: 권한 없는 메뉴는 accessible=false
     * REQ-CONTENT-002-D-3: 권한 매핑 없는 메뉴는 모든 인증 사용자에게 공개
     */
    @Override
    public List<MenuTreeNode> getMenuTreeForUser(Long siteId, Set<String> userPermissions) {
        List<Menu> menus = menuMapper.findBySiteId(siteId);
        return buildTreeForUser(menus, null, userPermissions);
    }

    /**
     * 메뉴 이름·URL·대상 수정.
     * REQ-CONTENT-001-D
     */
    @Override
    @Transactional
    @CacheEvict(value = "menuTree", allEntries = true)
    public MenuResponse updateMenu(Long id, MenuUpdateRequest request) {
        Menu menu = menuMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다. id=" + id));
        menu.setName(request.name());
        menu.setUrl(request.url());
        menu.setTarget(request.target());
        menuMapper.update(menu);
        return MenuResponse.from(menu);
    }

    /**
     * 메뉴 순서 변경.
     * REQ-CONTENT-001-D-3
     */
    @Override
    @Transactional
    @CacheEvict(value = "menuTree", allEntries = true)
    public MenuResponse changeOrder(Long id, MenuOrderRequest request) {
        Menu menu = menuMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다. id=" + id));
        menuMapper.updateSortOrder(id, request.newSortOrder());
        menu.setSortOrder(request.newSortOrder());
        return MenuResponse.from(menu);
    }

    /**
     * 메뉴 이동 (새 부모로).
     * REQ-CONTENT-001-D-4: 순환 참조 방지, 자손 path/depth 일괄 갱신
     *
     * // @MX:WARN: [AUTO] 자손 수가 많으면 updatePathAndDepth N회 호출 — 대규모 트리는 성능 검토 필요
     * // @MX:REASON: 재귀적 자손 갱신은 최악 O(N)이므로 트리 깊이/크기 제한 정책과 연동 필요
     */
    @Override
    @Transactional
    @CacheEvict(value = "menuTree", allEntries = true)
    public MenuResponse moveMenu(Long id, MenuMoveRequest request) {
        Menu menu = menuMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다. id=" + id));
        Menu newParent = menuMapper.findById(request.newParentId())
                .orElseThrow(() -> new IllegalArgumentException("새 부모 메뉴를 찾을 수 없습니다. id=" + request.newParentId()));

        // 순환 참조 검증: 이동 대상이 자신 혹은 자손인지 확인
        String currentPathPrefix = menu.getPath() + "/";
        List<Menu> descendants = menuMapper.findDescendants(currentPathPrefix);
        boolean isCircular = newParent.getId().equals(id) ||
                descendants.stream().anyMatch(d -> d.getId().equals(request.newParentId()));
        if (isCircular) {
            throw new MenuCircularReferenceException(id, request.newParentId());
        }

        // depth 검증
        short newDepth = (short) (newParent.getDepth() + 1);
        if (newDepth > MAX_DEPTH) {
            throw new MenuDepthExceededException(newDepth);
        }

        // 이전 path prefix 저장
        String oldPath = menu.getPath();
        String oldPathPrefix = oldPath + "/";

        // 새 path 계산
        String newPath = newParent.getPath() + "/" + id;
        menu.setParentId(request.newParentId());
        menu.setDepth(newDepth);
        menu.setPath(newPath);
        menuMapper.update(menu);

        // 자손 path/depth 일괄 갱신
        for (Menu desc : descendants) {
            String descNewPath = newPath + desc.getPath().substring(oldPath.length());
            short descNewDepth = (short) (desc.getDepth() - (short) (oldPath.split("/").length - 1) + (short) (newPath.split("/").length - 1));
            menuMapper.updatePathAndDepth(desc.getId(), descNewPath, descNewDepth);
        }

        return MenuResponse.from(menu);
    }

    /**
     * 메뉴 가시성 토글.
     * REQ-CONTENT-001-D-5
     */
    @Override
    @Transactional
    @CacheEvict(value = "menuTree", allEntries = true)
    public MenuResponse toggleVisibility(Long id, boolean isVisible) {
        Menu menu = menuMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다. id=" + id));
        menuMapper.updateVisibility(id, isVisible);
        menu.setVisible(isVisible);
        return MenuResponse.from(menu);
    }

    /**
     * 메뉴 삭제.
     * REQ-CONTENT-001-D-6: ON DELETE CASCADE는 DDL에서 처리
     */
    @Override
    @Transactional
    @CacheEvict(value = "menuTree", allEntries = true)
    public void deleteMenu(Long id) {
        menuMapper.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다. id=" + id));
        menuMapper.deleteById(id);
    }

    /**
     * 메뉴-권한 일괄 교체.
     * REQ-CONTENT-002-D-1: DELETE + INSERT 트랜잭션
     */
    @Override
    @Transactional
    public void replaceMenuPermissions(Long menuId, MenuPermissionRequest request) {
        menuMapper.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다. id=" + menuId));
        // 기존 권한 전체 삭제
        menuMapper.deletePermissionsByMenuId(menuId);
        // 신규 권한 일괄 INSERT
        if (request.permissionCodes() != null && !request.permissionCodes().isEmpty()) {
            List<MenuPermission> permissions = request.permissionCodes().stream()
                    .map(code -> MenuPermission.builder()
                            .menuId(menuId)
                            .permissionCode(code)
                            .build())
                    .collect(Collectors.toList());
            menuMapper.insertPermissions(permissions);
        }
    }

    // ─── private helpers ───────────────────────────────────────────────────────

    /**
     * 플랫 메뉴 목록을 재귀적으로 트리 구조로 변환 (공개 트리용).
     */
    private List<MenuTreeNode> buildTree(List<Menu> menus, Long parentId, boolean accessible) {
        List<MenuTreeNode> result = new ArrayList<>();
        for (Menu menu : menus) {
            boolean match = (parentId == null)
                    ? menu.getParentId() == null
                    : parentId.equals(menu.getParentId());
            if (match) {
                List<MenuTreeNode> children = buildTree(menus, menu.getId(), accessible);
                result.add(new MenuTreeNode(
                        menu.getId(), menu.getParentId(), menu.getCode(), menu.getName(),
                        menu.getUrl(), menu.getTarget(), menu.getIcon(), menu.getSortOrder(),
                        menu.getDepth(), menu.getPath(), menu.isVisible(), accessible, children
                ));
            }
        }
        return result;
    }

    /**
     * 사용자 권한 기반 트리 변환.
     * 권한 매핑이 없으면 공개(accessible=true), 있으면 userPermissions와 교집합 여부로 결정.
     */
    private List<MenuTreeNode> buildTreeForUser(List<Menu> menus, Long parentId, Set<String> userPermissions) {
        List<MenuTreeNode> result = new ArrayList<>();
        for (Menu menu : menus) {
            boolean match = (parentId == null)
                    ? menu.getParentId() == null
                    : parentId.equals(menu.getParentId());
            if (match) {
                List<String> requiredCodes = menuMapper.findPermissionCodesByMenuId(menu.getId());
                // REQ-CONTENT-002-D-3: 권한 매핑 없으면 공개
                boolean accessible = requiredCodes.isEmpty()
                        || requiredCodes.stream().anyMatch(userPermissions::contains);
                List<MenuTreeNode> children = buildTreeForUser(menus, menu.getId(), userPermissions);
                result.add(new MenuTreeNode(
                        menu.getId(), menu.getParentId(), menu.getCode(), menu.getName(),
                        menu.getUrl(), menu.getTarget(), menu.getIcon(), menu.getSortOrder(),
                        menu.getDepth(), menu.getPath(), menu.isVisible(), accessible, children
                ));
            }
        }
        return result;
    }
}
