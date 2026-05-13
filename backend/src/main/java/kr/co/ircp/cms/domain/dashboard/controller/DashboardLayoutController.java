package kr.co.ircp.cms.domain.dashboard.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutRequest;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutResponse;
import kr.co.ircp.cms.domain.dashboard.service.DashboardLayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 대시보드 레이아웃 REST 컨트롤러.
 * REQ-VIZ-002
 */
@RestController
@RequestMapping("/api/v1/dashboard/layouts")
@RequiredArgsConstructor
public class DashboardLayoutController {

    private final DashboardLayoutService service;

    @GetMapping
    public ResponseEntity<List<LayoutResponse>> list(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(required = false) List<String> roles) {
        return ResponseEntity.ok(service.listForUser(userId, roles == null ? List.of() : roles));
    }

    @PostMapping
    public ResponseEntity<LayoutResponse> create(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody LayoutRequest req) {
        return ResponseEntity.ok(service.create(userId, req));
    }

    /**
     * REQ-VIZ-001-D-5 A-5: 레이아웃 단건 조회 시 사용자 역할 기반 위젯 묵시적 필터링.
     *
     * <p>SecurityContext 에서 ROLE_* authority 를 추출해 service.getByIdForUser 에 전달한다.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LayoutResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getByIdForUser(id, resolveRoles()));
    }

    /** SecurityContext 의 ROLE_* authority 를 ROLE_ prefix 제거된 역할 코드 리스트로 변환. */
    private List<String> resolveRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) return List.of();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<LayoutResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody LayoutRequest req) {
        return ResponseEntity.ok(service.update(id, userId, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** REQ-VIZ-002-D-5: 다른 레이아웃을 자기 것으로 deep-copy. */
    @PostMapping("/{id}/clone")
    public ResponseEntity<LayoutResponse> clone(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(service.clone(id, userId));
    }

    /** REQ-VIZ-002-D-4: 기본 레이아웃 지정 (one-default). */
    @PutMapping("/{id}/default")
    public ResponseEntity<Void> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        service.setDefault(id, userId);
        return ResponseEntity.noContent().build();
    }
}
