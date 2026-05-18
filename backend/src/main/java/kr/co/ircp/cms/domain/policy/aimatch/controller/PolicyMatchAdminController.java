package kr.co.ircp.cms.domain.policy.aimatch.controller;

import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchMetricsRequest;
import kr.co.ircp.cms.domain.policy.aimatch.dto.PolicyMatchMetricsResponse;
import kr.co.ircp.cms.domain.policy.aimatch.service.PolicyMatchAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 추천 품질 모니터링 운영자 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-017 — ROLE=ADMIN 필수(@PreAuthorize, SecurityConfig 이중 방어).
 * 모든 호출은 SPEC-CMS-005 audit_log AOP로 자동 적재된다(@AuditLog).
 */
// @MX:ANCHOR: [AUTO] PolicyMatchAdminController — ADMIN 전용 + 감사 로그 대상 (보안 경계)
// @MX:REASON: REQ-PM-017 권한·감사 invariant. @PreAuthorize/@AuditLog 계약 (AI-001 AiAdminController 패턴)
// @MX:SPEC: SPEC-CMS-AI-002
@RestController
@RequestMapping("/api/v1/admin/ai/policy-match")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PolicyMatchAdminController {

    private final PolicyMatchAdminService adminService;

    @GetMapping("/metrics")
    @AuditLog(action = "READ", entityType = "PolicyMatchMetrics")
    public ResponseEntity<PolicyMatchMetricsResponse> metrics(
            @RequestParam(name = "period", required = false, defaultValue = "DAILY") String period,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(adminService.getMetrics(
                new PolicyMatchMetricsRequest(period, from, to)));
    }
}
