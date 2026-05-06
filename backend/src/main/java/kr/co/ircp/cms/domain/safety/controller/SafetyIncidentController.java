package kr.co.ircp.cms.domain.safety.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.IncidentCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.IncidentDetail;
import kr.co.ircp.cms.domain.safety.dto.IncidentSummary;
import kr.co.ircp.cms.domain.safety.dto.IncidentUpdateRequest;
import kr.co.ircp.cms.domain.safety.dto.SyncResult;
import kr.co.ircp.cms.domain.safety.service.SafetyIncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 사고사례 REST 컨트롤러.
 * REQ-SAFETY-001
 */
@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
public class SafetyIncidentController {

    private final SafetyIncidentService incidentService;

    /** GET /api/v1/safety/incidents */
    @GetMapping("/incidents")
    public ResponseEntity<PageResponse<IncidentSummary>> listIncidents(
            @RequestParam(required = false) String industryCode,
            @RequestParam(required = false) String incidentType,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(incidentService.listIncidents(industryCode, incidentType, severity, page, size));
    }

    /** GET /api/v1/safety/incidents/{id} */
    @GetMapping("/incidents/{id}")
    public ResponseEntity<IncidentDetail> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    /** POST /api/v1/safety/admin/incidents */
    @PostMapping("/admin/incidents")
    public ResponseEntity<IncidentDetail> createIncident(@Valid @RequestBody IncidentCreateRequest request) {
        IncidentDetail created = incidentService.createIncident(request);
        return ResponseEntity.created(URI.create("/api/v1/safety/incidents/" + created.id())).body(created);
    }

    /** PUT /api/v1/safety/admin/incidents/{id} */
    @PutMapping("/admin/incidents/{id}")
    public ResponseEntity<IncidentDetail> updateIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentUpdateRequest request) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request));
    }

    /** DELETE /api/v1/safety/admin/incidents/{id} */
    @DeleteMapping("/admin/incidents/{id}")
    public ResponseEntity<Void> archiveIncident(@PathVariable Long id) {
        incidentService.archiveIncident(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/safety/admin/incidents/sync */
    @PostMapping("/admin/incidents/sync")
    public ResponseEntity<SyncResult> sync(@RequestParam(defaultValue = "KOSHA_OPENAPI") String sourceType) {
        return ResponseEntity.ok(incidentService.triggerExternalSync(sourceType));
    }
}
