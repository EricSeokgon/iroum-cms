package kr.co.ircp.cms.domain.notification.template.admin.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateCreateRequest;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplatePreviewRequest;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplatePreviewResult;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateResponse;
import kr.co.ircp.cms.domain.notification.template.admin.dto.NotificationTemplateUpdateRequest;
import kr.co.ircp.cms.domain.notification.template.admin.service.NotificationTemplateService;
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

import java.net.URI;

/**
 * 알림 템플릿 관리자 REST 컨트롤러.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — 조회 NOTIFICATION_TEMPLATE:READ /
 * 생성·수정 NOTIFICATION_TEMPLATE:WRITE / 삭제 NOTIFICATION_TEMPLATE:DELETE 권한 게이트.
 */
// @MX:ANCHOR: [AUTO] NotificationTemplateAdminController — 알림 템플릿 관리 API 진입점 (READ/WRITE/DELETE 권한 분리)
// @MX:REASON: 보안 경계. 다수 메서드가 @PreAuthorize 계약 공유, NotificationTemplateService fan_in 집중점
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@RestController
@RequestMapping("/api/v1/notification/admin/template")
@RequiredArgsConstructor
public class NotificationTemplateAdminController {

    private final NotificationTemplateService templateService;

    @PostMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE:WRITE')")
    public ResponseEntity<NotificationTemplateResponse> create(
            @Valid @RequestBody NotificationTemplateCreateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        NotificationTemplateResponse created = templateService.create(request, actorId(principal));
        return ResponseEntity.created(URI.create("/api/v1/notification/admin/template/" + created.id()))
                .body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE:READ')")
    public ResponseEntity<PageResponse<NotificationTemplateResponse>> list(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(templateService.getAll(isActive, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE:READ')")
    public ResponseEntity<NotificationTemplateResponse> detail(@PathVariable("id") Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE:WRITE')")
    public ResponseEntity<NotificationTemplateResponse> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody NotificationTemplateUpdateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(templateService.update(id, request, actorId(principal)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE:DELETE')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('NOTIFICATION_TEMPLATE:READ')")
    public ResponseEntity<NotificationTemplatePreviewResult> preview(
            @PathVariable("id") Long id,
            @RequestBody NotificationTemplatePreviewRequest request) {
        return ResponseEntity.ok(templateService.previewTemplate(id, request.safeVariables()));
    }

    private Long actorId(JwtPrincipal principal) {
        return principal != null ? principal.userId() : null;
    }
}
