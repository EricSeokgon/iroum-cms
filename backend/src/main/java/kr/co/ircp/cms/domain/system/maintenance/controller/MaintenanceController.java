package kr.co.ircp.cms.domain.system.maintenance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceRequest;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceResponse;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 점검 모드 API 컨트롤러.
 * REQ-SYSTEM-005-D
 */
@Tag(name = "System Maintenance", description = "점검 모드 관리 API")
@RestController
@RequestMapping("/api/v1/system/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @Operation(summary = "점검 목록 조회")
    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM:MAINT:READ')")
    public ResponseEntity<List<MaintenanceResponse>> list() {
        return ResponseEntity.ok(maintenanceService.listAll());
    }

    @Operation(summary = "점검 단건 조회")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SYSTEM:MAINT:READ')")
    public ResponseEntity<MaintenanceResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.getById(id));
    }

    @Operation(summary = "점검 등록")
    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM:MAINT:WRITE')")
    public ResponseEntity<MaintenanceResponse> create(@Valid @RequestBody MaintenanceRequest request) {
        return ResponseEntity.status(201).body(maintenanceService.create(request));
    }

    @Operation(summary = "점검 즉시 활성화 (SCHEDULED → ACTIVE)")
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('SYSTEM:MAINT:WRITE')")
    public ResponseEntity<MaintenanceResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.activate(id));
    }
}
