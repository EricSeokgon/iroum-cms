package kr.co.ircp.cms.domain.content.menu.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.menu.dto.MenuMoveRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuOrderRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuPermissionRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuRequest;
import kr.co.ircp.cms.domain.content.menu.dto.MenuResponse;
import kr.co.ircp.cms.domain.content.menu.dto.MenuTreeNode;
import kr.co.ircp.cms.domain.content.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 메뉴 REST 컨트롤러.
 * REQ-CONTENT-001-D / REQ-CONTENT-002-D: 메뉴 트리 API
 */
@RestController
@RequestMapping("/api/v1/content/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** POST /api/v1/content/menus — 메뉴 생성 */
    @PostMapping
    @PreAuthorize("hasAuthority('MENU:WRITE')")
    public ResponseEntity<MenuResponse> createMenu(@Valid @RequestBody MenuRequest request) {
        MenuResponse created = menuService.createMenu(request);
        return ResponseEntity.created(URI.create("/api/v1/content/menus/" + created.id())).body(created);
    }

    /** GET /api/v1/content/menus/tree — 메뉴 트리 조회 */
    @GetMapping("/tree")
    public ResponseEntity<List<MenuTreeNode>> getMenuTree(@RequestParam Long siteId) {
        return ResponseEntity.ok(menuService.getMenuTree(siteId));
    }

    /** PATCH /api/v1/content/menus/{id}/order — 메뉴 순서 변경 */
    @PatchMapping("/{id}/order")
    @PreAuthorize("hasAuthority('MENU:WRITE')")
    public ResponseEntity<MenuResponse> changeOrder(
            @PathVariable Long id,
            @Valid @RequestBody MenuOrderRequest request
    ) {
        return ResponseEntity.ok(menuService.changeOrder(id, request));
    }

    /** PATCH /api/v1/content/menus/{id}/move — 메뉴 이동 */
    @PatchMapping("/{id}/move")
    @PreAuthorize("hasAuthority('MENU:WRITE')")
    public ResponseEntity<MenuResponse> moveMenu(
            @PathVariable Long id,
            @Valid @RequestBody MenuMoveRequest request
    ) {
        return ResponseEntity.ok(menuService.moveMenu(id, request));
    }

    /** PATCH /api/v1/content/menus/{id}/visibility — 가시성 토글 */
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAuthority('MENU:WRITE')")
    public ResponseEntity<MenuResponse> toggleVisibility(
            @PathVariable Long id,
            @RequestParam boolean isVisible
    ) {
        return ResponseEntity.ok(menuService.toggleVisibility(id, isVisible));
    }

    /** DELETE /api/v1/content/menus/{id} — 메뉴 삭제 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU:WRITE')")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/content/menus/{id}/permissions — 메뉴-권한 매핑 일괄 저장 */
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('MENU:PERMISSION:WRITE')")
    public ResponseEntity<Void> replacePermissions(
            @PathVariable Long id,
            @Valid @RequestBody MenuPermissionRequest request
    ) {
        menuService.replaceMenuPermissions(id, request);
        return ResponseEntity.noContent().build();
    }
}
