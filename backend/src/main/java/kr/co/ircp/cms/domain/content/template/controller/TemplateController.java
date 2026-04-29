package kr.co.ircp.cms.domain.content.template.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.content.template.dto.TemplateRequest;
import kr.co.ircp.cms.domain.content.template.dto.TemplateResponse;
import kr.co.ircp.cms.domain.content.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * 템플릿 REST 컨트롤러.
 * REQ-CONTENT-004-D: 템플릿 관리 API
 */
@RestController
@RequestMapping("/api/v1/content/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /** GET /api/v1/content/templates — 템플릿 목록 조회 */
    @GetMapping
    @PreAuthorize("hasAuthority('TEMPLATE:READ')")
    public ResponseEntity<List<TemplateResponse>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    /** GET /api/v1/content/templates/{id} — 템플릿 단건 조회 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TEMPLATE:READ')")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    /** POST /api/v1/content/templates — 템플릿 등록 */
    @PostMapping
    @PreAuthorize("hasAuthority('TEMPLATE:WRITE')")
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody TemplateRequest request) {
        TemplateResponse created = templateService.createTemplate(request);
        return ResponseEntity.created(URI.create("/api/v1/content/templates/" + created.id())).body(created);
    }

    /** PUT /api/v1/content/templates/{id} — 템플릿 수정 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TEMPLATE:WRITE')")
    public ResponseEntity<TemplateResponse> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequest request
    ) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    /** PATCH /api/v1/content/templates/{id}/status — 상태 변경 */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('TEMPLATE:WRITE')")
    public ResponseEntity<TemplateResponse> changeStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        return ResponseEntity.ok(templateService.changeStatus(id, status));
    }
}
