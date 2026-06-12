package kr.co.ircp.cms.domain.auth.menu;

import kr.co.ircp.cms.domain.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 어드민 메뉴 접근 제어 서비스 구현.
 *
 * <p>SPEC-CMS-RBAC-001 REQ-RBAC-002 — 사용자 유효 권한 집합으로 접근 가능 메뉴 트리 산출.
 */
// @MX:NOTE: [AUTO] AdminMenuServiceImpl — OR 의미 권한 매핑 + 부모-자식 트리 가시성 규칙
// @MX:SPEC: SPEC-CMS-RBAC-001
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMenuServiceImpl implements AdminMenuService {

    private final AdminMenuMapper adminMenuMapper;
    private final PermissionService permissionService;

    @Override
    public List<AccessibleMenu> findAccessibleMenus(long userId) {
        Set<String> userPermissions = permissionService.findEffectivePermissionsForUser(userId);

        List<AdminMenuMeta> menus = adminMenuMapper.findActiveMenus();
        Map<String, Set<String>> permissionsByMenu = groupPermissionsByMenu(
                adminMenuMapper.findActiveMenuPermissions());

        // 자식 목록을 부모 키별로 그룹화 (sort_order 정렬 보존)
        Map<String, List<AdminMenuMeta>> childrenByParent = new LinkedHashMap<>();
        List<AdminMenuMeta> roots = new ArrayList<>();
        for (AdminMenuMeta menu : menus) {
            if (menu.parentKey() == null) {
                roots.add(menu);
            } else {
                childrenByParent.computeIfAbsent(menu.parentKey(), k -> new ArrayList<>()).add(menu);
            }
        }

        List<AccessibleMenu> result = new ArrayList<>();
        for (AdminMenuMeta root : roots) {
            AccessibleMenu node = buildAccessibleNode(
                    root, childrenByParent, permissionsByMenu, userPermissions);
            if (node != null) {
                result.add(node);
            }
        }
        result.sort(Comparator.comparingInt(AccessibleMenu::sortOrder));
        return result;
    }

    /** 매핑 행을 menuKey → permissionCode 집합으로 그룹화. */
    private Map<String, Set<String>> groupPermissionsByMenu(List<AdminMenuPermissionRow> rows) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (AdminMenuPermissionRow row : rows) {
            map.computeIfAbsent(row.menuKey(), k -> new HashSet<>()).add(row.permissionCode());
        }
        return map;
    }

    /**
     * 메뉴 노드를 접근 가능 트리 노드로 변환.
     *
     * <p>가시성 규칙:
     * <ul>
     *   <li>리프 메뉴: selfAccessible 이면 포함</li>
     *   <li>그룹 메뉴(자식 보유): 접근 가능한 자식이 하나라도 있으면 포함(부모 트리 노출)</li>
     * </ul>
     * selfAccessible = 매핑 권한이 없음(무제한) OR 사용자 권한과 교집합 존재(OR 의미).
     *
     * @return 접근 불가 시 null
     */
    private AccessibleMenu buildAccessibleNode(
            AdminMenuMeta menu,
            Map<String, List<AdminMenuMeta>> childrenByParent,
            Map<String, Set<String>> permissionsByMenu,
            Set<String> userPermissions) {

        Set<String> required = permissionsByMenu.getOrDefault(menu.menuKey(), Set.of());
        boolean selfAccessible = isAccessible(required, userPermissions);

        List<AdminMenuMeta> rawChildren = childrenByParent.get(menu.menuKey());
        List<AccessibleMenu> children = new ArrayList<>();
        if (rawChildren != null) {
            for (AdminMenuMeta child : rawChildren) {
                AccessibleMenu childNode = buildAccessibleNode(
                        child, childrenByParent, permissionsByMenu, userPermissions);
                if (childNode != null) {
                    children.add(childNode);
                }
            }
            children.sort(Comparator.comparingInt(AccessibleMenu::sortOrder));
        }

        boolean isGroup = rawChildren != null && !rawChildren.isEmpty();
        if (isGroup) {
            // 그룹: 접근 가능한 자식이 있어야 노출. 자식이 모두 차단되면 그룹도 숨김.
            if (children.isEmpty()) {
                return null;
            }
        } else {
            // 리프: 자기 권한 검사만
            if (!selfAccessible) {
                return null;
            }
        }

        return new AccessibleMenu(
                menu.menuKey(), menu.name(), menu.routePath(), menu.icon(),
                menu.sortOrder(), children);
    }

    /** OR 의미 접근 판정: 매핑 권한 없음(무제한) OR 사용자 권한과 교집합 존재. */
    private boolean isAccessible(Set<String> required, Set<String> userPermissions) {
        if (required.isEmpty()) {
            return true;
        }
        return required.stream().anyMatch(userPermissions::contains);
    }
}
