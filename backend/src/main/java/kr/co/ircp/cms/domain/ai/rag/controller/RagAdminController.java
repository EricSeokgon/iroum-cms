package kr.co.ircp.cms.domain.ai.rag.controller;

import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsQuery;
import kr.co.ircp.cms.domain.ai.rag.dto.RagMetricsResponse;
import kr.co.ircp.cms.domain.ai.rag.service.RagMetricsService;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
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
 * RAG 품질 모니터링 운영자 REST 컨트롤러.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-015/016/019 — ROLE=ADMIN 필수(@PreAuthorize,
 * SecurityConfig 이중 방어). 모든 호출은 SPEC-CMS-005 audit_log AOP로
 * 자동 적재된다(@AuditLog). 비ADMIN 호출은 403(본문 미제공).
 */
// @MX:ANCHOR: [AUTO] RagAdminController — ADMIN 전용 + 감사 로그 대상 (보안 경계)
// @MX:REASON: REQ-RAG-016/019 권한·감사 invariant. @PreAuthorize/@AuditLog 계약 (AI-002 패턴)
// @MX:SPEC: SPEC-CMS-AI-003
@RestController
@RequestMapping("/api/v1/admin/ai/rag")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RagAdminController {

    private final RagMetricsService ragMetricsService;

    @GetMapping("/metrics")
    @AuditLog(action = "READ", entityType = "RagMetrics")
    public ResponseEntity<RagMetricsResponse> metrics(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                ragMetricsService.getMetrics(new RagMetricsQuery(from, to)));
    }
}
