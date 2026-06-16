package kr.co.ircp.cms.domain.email.template.admin.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigRequest;
import kr.co.ircp.cms.domain.email.template.admin.dto.SmtpConfigResponse;
import kr.co.ircp.cms.domain.email.template.admin.service.SmtpConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SMTP 동적 설정 관리자 REST 컨트롤러.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-040/041/042/061/062 —
 * 조회 EMAIL_TEMPLATE:READ(비밀번호 마스킹) / 변경 EMAIL_TEMPLATE:WRITE.
 */
// @MX:NOTE: [AUTO] SPEC-CMS-EMAIL-TEMPLATE-001 — SMTP 설정 조회(마스킹)/변경(재시작 없이 적용)
// @MX:SPEC: SPEC-CMS-EMAIL-TEMPLATE-001#REQ-ET-040
@RestController
@RequestMapping("/api/v1/admin/smtp-config")
@RequiredArgsConstructor
public class SmtpConfigAdminController {

    private final SmtpConfigService smtpConfigService;

    @GetMapping
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:READ')")
    public ResponseEntity<SmtpConfigResponse> getActive() {
        return ResponseEntity.ok(smtpConfigService.getActive());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EMAIL_TEMPLATE:WRITE')")
    public ResponseEntity<SmtpConfigResponse> update(
            @Valid @RequestBody SmtpConfigRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        Long actor = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(smtpConfigService.update(request, actor));
    }
}
