package kr.co.ircp.cms.domain.email.template.admin.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateCreateRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplatePreviewRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.dto.EmailTemplateUpdateRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.PagedResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.RenderResult;
import kr.co.ircp.cms.domain.email.template.admin.dto.SendLogResponse;
import kr.co.ircp.cms.domain.email.template.admin.dto.SendLogSearchCriteria;
import kr.co.ircp.cms.domain.email.template.admin.dto.TestSendRequest;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateSendLogService;
import kr.co.ircp.cms.domain.email.template.admin.service.EmailTemplateService;
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

/**
 * 이메일 템플릿 관리자 REST 컨트롤러.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-001~005, 020, 021, 051, 060~063 —
 * 조회 EMAIL_TEMPLATE:READ / 생성·수정·미리보기·테스트발송 EMAIL_TEMPLATE:WRITE /
 * 삭제 EMAIL_TEMPLATE:DELETE 권한 게이트.
 */
// @MX:ANCHOR: [AUTO] EmailTemplateAdminController — 이메일 템플릿 관리 API 진입점 (READ/WRITE/DELETE 권한 분리)
// @MX:REASON: 보안 경계. 다수 메서드가 @PreAuthorize 계약 공유, EmailTemplateService fan_in 집중점
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-060
@RestController
@RequestMapping("/api/v1/admin/email-templates")
@RequiredArgsConstructor
public class EmailTemplateAdminController {

    private final EmailTemplateService templateService;
    private final EmailTemplateSendLogService sendLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:READ')")
    public ResponseEntity<PagedResponse<EmailTemplateResponse>> list(
            @RequestParam(name = "templateType", required = false) String templateType,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        var criteria = new EmailTemplateSearchCriteria(templateType, language, isActive, keyword, page, size);
        return ResponseEntity.ok(templateService.list(criteria));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:READ')")
    public ResponseEntity<EmailTemplateResponse> detail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(templateService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:WRITE')")
    public ResponseEntity<EmailTemplateResponse> create(
            @Valid @RequestBody EmailTemplateCreateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        EmailTemplateResponse created = templateService.create(request, actorId(principal));
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:WRITE')")
    public ResponseEntity<EmailTemplateResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody EmailTemplateUpdateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(templateService.update(id, request, actorId(principal)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:DELETE')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:WRITE')")
    public ResponseEntity<RenderResult> preview(
            @PathVariable("id") Long id,
            @RequestBody EmailTemplatePreviewRequest request) {
        return ResponseEntity.ok(templateService.preview(id, request.safeVariables()));
    }

    @PostMapping("/{id}/test-send")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:WRITE')")
    public ResponseEntity<Void> testSend(
            @PathVariable("id") Long id,
            @RequestBody TestSendRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        templateService.testSend(id, actorId(principal), request.safeVariables());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/send-logs")
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:READ')")
    public ResponseEntity<PagedResponse<SendLogResponse>> sendLogs(
            @PathVariable("id") Long id,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        var criteria = new SendLogSearchCriteria(id, status, null, null, page, size);
        return ResponseEntity.ok(sendLogService.search(criteria));
    }

    private Long actorId(JwtPrincipal principal) {
        return principal != null ? principal.userId() : null;
    }
}
