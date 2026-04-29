package kr.co.ircp.cms.domain.content.menu.service;

import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;
import kr.co.ircp.cms.domain.content.menu.entity.Menu;
import kr.co.ircp.cms.domain.content.menu.mapper.MenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MenuService 권한 매핑 RED 단계 테스트.
 * REQ-CONTENT-002-D: 메뉴-권한 매핑 (replace, 필터 트리)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MenuPermissionService RED 테스트 (REQ-CONTENT-002-D)")
class MenuPermissionServiceTest {

    @Mock
    private MenuMapper menuMapper;

    private MenuService menuService;

    @BeforeEach
    void setUp() {
        menuService = new MenuServiceImpl(menuMapper);
    }

    private Menu stubMenu(long id, Long parentId, short depth, String path, String code) {
        return Menu.builder()
                .id(id)
                .siteId(1L)
                .parentId(parentId)
                .code(code)
                .name("메뉴 " + id)
                .url("/menu/" + code.toLowerCase())
                .target("_self")
                .depth(depth)
                .path(path)
                .isVisible(true)
                .status("ACTIVE")
                .sortOrder(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-002-D-1: 메뉴 권한 일괄 교체(replace)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("메뉴-권한 일괄 교체 — 기존 삭제 후 신규 INSERT")
    void shouldReplaceMenuPermissionsBulk() {
        // Arrange
        Menu menu = stubMenu(1L, null, (short) 1, "/1", "HOME");
        when(menuMapper.findById(1L)).thenReturn(Optional.of(menu));
        when(menuMapper.deletePermissionsByMenuId(1L)).thenReturn(2);
        MenuPermissionRequest request = new MenuPermissionRequest(
                List.of("MENU:HOME:READ", "MENU:HOME:WRITE")
        );

        // Act
        menuService.replaceMenuPermissions(1L, request);

        // Assert — 기존 삭제 호출 확인
        verify(menuMapper).deletePermissionsByMenuId(1L);
        // 신규 INSERT 호출 확인 (non-empty permissions)
        verify(menuMapper).insertPermissions(any());
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-002-D-2: 사용자 권한 기반 메뉴 트리 필터
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("사용자 권한 기반 메뉴 트리 — 접근 불가 메뉴는 accessible=false")
    void shouldFilterMenuTreeByUserPermissions() {
        // Arrange
        Menu publicMenu = stubMenu(1L, null, (short) 1, "/1", "HOME");
        Menu restrictedMenu = stubMenu(2L, null, (short) 1, "/2", "ADMIN_MENU");
        when(menuMapper.findBySiteId(1L)).thenReturn(List.of(publicMenu, restrictedMenu));
        when(menuMapper.findPermissionCodesByMenuId(1L)).thenReturn(Collections.emptyList());
        when(menuMapper.findPermissionCodesByMenuId(2L)).thenReturn(List.of("ADMIN:ACCESS"));
        // 사용자는 ADMIN:ACCESS 권한 없음
        Set<String> userPermissions = Set.of("MENU:HOME:READ");

        // Act
        List<MenuTreeNode> tree = menuService.getMenuTreeForUser(1L, userPermissions);

        // Assert
        assertThat(tree).isNotNull();
        MenuTreeNode adminNode = tree.stream()
                .filter(n -> "ADMIN_MENU".equals(n.code()))
                .findFirst()
                .orElseThrow();
        assertThat(adminNode.accessible()).isFalse();
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-002-D-3: 권한 미설정 메뉴는 공개
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("권한 미설정 메뉴는 모든 사용자에게 accessible=true")
    void shouldExposePublicMenuWhenNoPermissionsMapped() {
        // Arrange
        Menu publicMenu = stubMenu(1L, null, (short) 1, "/1", "HOME");
        when(menuMapper.findBySiteId(1L)).thenReturn(List.of(publicMenu));
        when(menuMapper.findPermissionCodesByMenuId(1L)).thenReturn(Collections.emptyList());
        // 권한 없는 사용자
        Set<String> noPermissions = Collections.emptySet();

        // Act
        List<MenuTreeNode> tree = menuService.getMenuTreeForUser(1L, noPermissions);

        // Assert
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).accessible()).isTrue();
    }
}
