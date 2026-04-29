package kr.co.ircp.cms.domain.content.menu.service;

import kr.co.ircp.cms.domain.content.menu.dto.MenuMoveRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuOrderRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuResponse;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;
import kr.co.ircp.cms.domain.content.menu.entity.Menu;
import kr.co.ircp.cms.domain.content.menu.exception.MenuCircularReferenceException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuCodeDuplicateException;
import kr.co.ircp.cms.domain.content.menu.exception.MenuDepthExceededException;
import kr.co.ircp.cms.domain.content.menu.mapper.MenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MenuService RED 단계 테스트.
 * REQ-CONTENT-001-D: 메뉴 트리 CRUD + 이동 + 순서변경
 * REQ-CONTENT-002-D: 메뉴-권한 매핑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MenuService RED 테스트 (REQ-CONTENT-001-D, REQ-CONTENT-002-D)")
class MenuServiceTest {

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

    private MenuRequest stubRequest(Long siteId, Long parentId, String code, int sortOrder) {
        return new MenuRequest(siteId, parentId, code, "테스트 메뉴", "/test", "_self", null, sortOrder, true, null);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-1: 메뉴 생성 — depth 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("depth 5 초과 메뉴 생성 시 MenuDepthExceededException 발생")
    void shouldCreateMenuWithDepthValidation() {
        // Arrange — depth 5 부모 메뉴 (depth 6 자식 불가)
        Menu depth5Parent = stubMenu(10L, 9L, (short) 5, "/1/2/3/4/10", "D5PARENT");
        when(menuMapper.findById(10L)).thenReturn(Optional.of(depth5Parent));
        when(menuMapper.existsBySiteIdAndCode(1L, "OVERFLOW")).thenReturn(false);
        MenuRequest request = stubRequest(1L, 10L, "OVERFLOW", 0);

        // Act & Assert
        assertThatThrownBy(() -> menuService.createMenu(request))
                .isInstanceOf(MenuDepthExceededException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-1: 메뉴 생성 — 코드 유일성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("같은 사이트 내 중복 코드로 메뉴 생성 시 MenuCodeDuplicateException 발생")
    void shouldCreateMenuWithCodeUniqueness() {
        // Arrange
        when(menuMapper.existsBySiteIdAndCode(1L, "HOME")).thenReturn(true);
        MenuRequest request = stubRequest(1L, null, "HOME", 0);

        // Act & Assert
        assertThatThrownBy(() -> menuService.createMenu(request))
                .isInstanceOf(MenuCodeDuplicateException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-2: 메뉴 트리 중첩 구조 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("메뉴 트리 조회 — 루트-자식 중첩 구조 반환")
    void shouldGetMenuTreeNested() {
        // Arrange
        Menu root = stubMenu(1L, null, (short) 1, "/1", "HOME");
        Menu child = stubMenu(2L, 1L, (short) 2, "/1/2", "NOTICE");
        when(menuMapper.findBySiteId(1L)).thenReturn(List.of(root, child));

        // Act
        List<MenuTreeNode> tree = menuService.getMenuTree(1L);

        // Assert
        assertThat(tree).isNotNull();
        assertThat(tree).isNotEmpty();
        // 루트 노드가 최상위에 있고, children에 자식이 포함되어야 함
        MenuTreeNode rootNode = tree.stream()
                .filter(n -> "HOME".equals(n.code()))
                .findFirst()
                .orElseThrow();
        assertThat(rootNode.parentId()).isNull();
        assertThat(rootNode.children()).hasSize(1);
        assertThat(rootNode.children().get(0).code()).isEqualTo("NOTICE");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-3: 메뉴 순서 변경
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("메뉴 순서 변경 — sortOrder 갱신 성공")
    void shouldChangeMenuOrderWithoutConflict() {
        // Arrange
        Menu menu = stubMenu(1L, null, (short) 1, "/1", "HOME");
        when(menuMapper.findById(1L)).thenReturn(Optional.of(menu));
        when(menuMapper.updateSortOrder(1L, 5)).thenReturn(1);
        MenuOrderRequest request = new MenuOrderRequest(5);

        // Act
        MenuResponse response = menuService.changeOrder(1L, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.sortOrder()).isEqualTo(5);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-4: 메뉴 이동 — 순환 참조 방지
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("자손 메뉴를 새 부모로 이동 시 MenuCircularReferenceException 발생")
    void shouldMoveMenuRejectingCircularReference() {
        // Arrange — menu(1)의 자손이 menu(5), menu(5)를 menu(1)의 newParent로 지정 시 순환
        Menu parent = stubMenu(1L, null, (short) 1, "/1", "ROOT");
        Menu descendant = stubMenu(5L, 1L, (short) 2, "/1/5", "CHILD");
        when(menuMapper.findById(1L)).thenReturn(Optional.of(parent));
        when(menuMapper.findById(5L)).thenReturn(Optional.of(descendant));
        // 자손 검색: /1 prefix로 시작하는 메뉴 = menu(1) 자체와 자손 포함
        when(menuMapper.findDescendants("/1/")).thenReturn(List.of(descendant));
        MenuMoveRequest request = new MenuMoveRequest(5L);

        // Act & Assert
        assertThatThrownBy(() -> menuService.moveMenu(1L, request))
                .isInstanceOf(MenuCircularReferenceException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-4: 메뉴 이동 — 자손 경로 갱신
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("메뉴 이동 시 자손 메뉴 path/depth 일괄 갱신")
    void shouldMoveMenuUpdatingDescendantPaths() {
        // Arrange — menu(3)을 menu(2) 아래로 이동
        Menu movingMenu = stubMenu(3L, 1L, (short) 2, "/1/3", "MOVING");
        Menu newParent = stubMenu(2L, null, (short) 1, "/2", "NEWPARENT");
        Menu child = stubMenu(7L, 3L, (short) 3, "/1/3/7", "MCHILD");
        when(menuMapper.findById(3L)).thenReturn(Optional.of(movingMenu));
        when(menuMapper.findById(2L)).thenReturn(Optional.of(newParent));
        when(menuMapper.findDescendants("/1/3/")).thenReturn(List.of(child));
        when(menuMapper.update(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(menuMapper.updatePathAndDepth(anyLong(), anyString(), org.mockito.ArgumentMatchers.anyShort())).thenReturn(1);
        MenuMoveRequest request = new MenuMoveRequest(2L);

        // Act
        MenuResponse response = menuService.moveMenu(3L, request);

        // Assert
        assertThat(response).isNotNull();
        // 이동 후 path는 새 부모 경로 기준이어야 함
        assertThat(response.path()).startsWith("/2/");
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-5: 가시성 토글
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("메뉴 가시성 토글 — isVisible 반전")
    void shouldToggleMenuVisibility() {
        // Arrange
        Menu menu = stubMenu(1L, null, (short) 1, "/1", "HOME");
        when(menuMapper.findById(1L)).thenReturn(Optional.of(menu));
        when(menuMapper.updateVisibility(1L, false)).thenReturn(1);

        // Act
        MenuResponse response = menuService.toggleVisibility(1L, false);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.isVisible()).isFalse();
    }

    // ──────────────────────────────────────────────
    // REQ-CONTENT-001-D-6: 메뉴 삭제 — 자손 CASCADE
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("메뉴 삭제 시 자손 포함 CASCADE 삭제")
    void shouldDeleteMenuCascadingDescendants() {
        // Arrange
        Menu menu = stubMenu(1L, null, (short) 1, "/1", "HOME");
        when(menuMapper.findById(1L)).thenReturn(Optional.of(menu));
        when(menuMapper.deleteById(1L)).thenReturn(1);

        // Act — 예외 없이 완료되어야 함
        menuService.deleteMenu(1L);

        // Assert (삭제 호출 검증)
        org.mockito.Mockito.verify(menuMapper).deleteById(1L);
    }
}
