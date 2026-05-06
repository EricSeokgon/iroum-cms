package kr.co.ircp.cms.domain.dashboard.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetDataResponse;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetRequest;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetResponse;
import kr.co.ircp.cms.domain.dashboard.service.DashboardWidgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 위젯 REST 컨트롤러.
 * REQ-VIZ-001 (위젯 CRUD), REQ-VIZ-005 (데이터)
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardWidgetController {

    private final DashboardWidgetService service;

    @GetMapping("/widgets")
    public ResponseEntity<List<WidgetResponse>> list(
            @RequestParam(required = false) String widgetType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(widgetType, status, page, size));
    }

    @PostMapping("/widgets")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<WidgetResponse> create(
            @Valid @RequestBody WidgetRequest req,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(service.create(req, userId));
    }

    @GetMapping("/widgets/{id}")
    public ResponseEntity<WidgetResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/widgets/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<WidgetResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody WidgetRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/widgets/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/widgets/preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
    public ResponseEntity<WidgetDataResponse> preview(
            @Valid @RequestBody WidgetRequest req,
            @RequestParam(required = false) List<String> roles) {
        return ResponseEntity.ok(service.preview(req,
                roles == null ? List.of("DEPT_ADMIN") : roles));
    }

    /** REQ-VIZ-005-D-1: 위젯 차트 데이터셋 조회. */
    @GetMapping("/widgets/{id}/data")
    public ResponseEntity<WidgetDataResponse> getData(
            @PathVariable Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String dim,
            @RequestParam(required = false) List<String> roles) {
        Map<String, Object> filters = new HashMap<>();
        if (from != null) filters.put("from", from);
        if (to != null) filters.put("to", to);
        if (dim != null) filters.put("dim", dim);
        return ResponseEntity.ok(service.getData(id, filters,
                roles == null ? List.of("VIEWER") : roles));
    }

    /** 시계열 데이터 (group 차원 별도 적용). */
    @GetMapping("/widgets/{id}/data/series")
    public ResponseEntity<WidgetDataResponse> getSeries(
            @PathVariable Long id,
            @RequestParam(required = false) String dim,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) List<String> roles) {
        Map<String, Object> filters = new HashMap<>();
        if (dim != null) filters.put("dim", dim);
        if (group != null) filters.put("group", group);
        return ResponseEntity.ok(service.getData(id, filters,
                roles == null ? List.of("VIEWER") : roles));
    }
}
