package kr.co.ircp.cms.domain.dashboard.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewRequest;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewResponse;
import kr.co.ircp.cms.domain.dashboard.service.SavedViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

/**
 * 저장된 뷰 REST 컨트롤러.
 * REQ-VIZ-004
 */
@RestController
@RequestMapping("/api/v1/dashboard/views")
@RequiredArgsConstructor
public class SavedViewController {

    private final SavedViewService service;

    @GetMapping
    public ResponseEntity<List<SavedViewResponse>> list(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(name = "dashboard_id", required = false) Long dashboardId) {
        return ResponseEntity.ok(service.listForUser(userId, dashboardId));
    }

    @PostMapping
    public ResponseEntity<SavedViewResponse> create(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody SavedViewRequest req) {
        return ResponseEntity.ok(service.create(userId, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SavedViewResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody SavedViewRequest req) {
        return ResponseEntity.ok(service.update(id, userId, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        service.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    /** REQ-VIZ-004-D-5: 뷰 적용 (last_used_at 갱신). */
    @PostMapping("/{id}/apply")
    public ResponseEntity<SavedViewResponse> apply(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(service.apply(id, userId));
    }
}
