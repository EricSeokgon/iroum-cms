package kr.co.ircp.cms.domain.safety.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.safety.dto.ChecklistItemRequest;
import kr.co.ircp.cms.domain.safety.dto.ChecklistItemResponse;
import kr.co.ircp.cms.domain.safety.dto.PreviewRequest;
import kr.co.ircp.cms.domain.safety.dto.PreviewResponse;
import kr.co.ircp.cms.domain.safety.dto.TemplateRequest;
import kr.co.ircp.cms.domain.safety.dto.TemplateResponse;
import kr.co.ircp.cms.domain.safety.service.SafetyTemplateService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 가이드라인 템플릿 관리 REST 컨트롤러.
 * REQ-SAFETY-005
 */
@RestController
@RequestMapping("/api/v1/safety/admin/templates")
@RequiredArgsConstructor
public class SafetyTemplateController {

    private final SafetyTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody TemplateRequest request,
            @AuthenticationPrincipal Long createdBy) {
        TemplateResponse created = templateService.createTemplate(request, createdBy);
        return ResponseEntity.created(URI.create("/api/v1/safety/admin/templates/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponse> releaseNewVersion(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequest request,
            @AuthenticationPrincipal Long createdBy) {
        return ResponseEntity.ok(templateService.releaseNewVersion(id, request, createdBy));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<PreviewResponse> preview(
            @PathVariable Long id,
            @RequestBody(required = false) PreviewRequest request) {
        return ResponseEntity.ok(templateService.previewTemplate(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        templateService.archiveTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/checklist")
    public ResponseEntity<List<ChecklistItemResponse>> listChecklist(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.listChecklistItems(id));
    }

    @PostMapping("/{id}/checklist")
    public ResponseEntity<ChecklistItemResponse> addChecklist(
            @PathVariable Long id,
            @Valid @RequestBody ChecklistItemRequest request) {
        return ResponseEntity.ok(templateService.addChecklistItem(id, request));
    }
}
