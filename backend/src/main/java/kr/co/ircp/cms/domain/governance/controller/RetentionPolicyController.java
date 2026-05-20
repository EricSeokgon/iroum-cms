package kr.co.ircp.cms.domain.governance.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.governance.batch.AccessLogRetentionJob;
import kr.co.ircp.cms.domain.governance.batch.AuditLogArchiveJob;
import kr.co.ircp.cms.domain.governance.batch.GovernanceJobSupport;
import kr.co.ircp.cms.domain.governance.batch.IntegrationLogRetentionJob;
import kr.co.ircp.cms.domain.governance.batch.LoginHistoryPurgeJob;
import kr.co.ircp.cms.domain.governance.batch.PersonalDataRetentionJob;
import kr.co.ircp.cms.domain.governance.dto.RetentionPolicyRequest;
import kr.co.ircp.cms.domain.governance.dto.RetentionPolicyResponse;
import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.governance.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 보존 정책 REST 컨트롤러.
 *
 * <p>SPEC-CMS-009 REQ-GOV-006~009.
 */
// @MX:ANCHOR: [AUTO] RetentionPolicyController — 4개 엔드포인트 (목록, 생성, 수정, 수동 실행)
// @MX:REASON: 보존 정책 관리 + 5개 RetentionJob 수동 트리거의 진입점
// @MX:SPEC: SPEC-CMS-009#REQ-GOV-006
@RestController
@RequestMapping("/api/v1/governance/retention-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RetentionPolicyController {

    private final RetentionPolicyService service;
    private final BatchExecutionLogService batchLog;
    private final PersonalDataRetentionJob personalJob;
    private final AuditLogArchiveJob auditJob;
    private final LoginHistoryPurgeJob loginJob;
    private final AccessLogRetentionJob accessJob;
    private final IntegrationLogRetentionJob integrationJob;

    @GetMapping
    public ResponseEntity<List<RetentionPolicyResponse>> list() {
        List<RetentionPolicyResponse> ret = service.findAll().stream()
                .map(RetentionPolicyResponse::from)
                .toList();
        return ResponseEntity.ok(ret);
    }

    @PostMapping
    public ResponseEntity<RetentionPolicyResponse> create(
            @Valid @RequestBody RetentionPolicyRequest req,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        RetentionPolicy entity = build(req, null, userId);
        RetentionPolicy created = service.create(entity);
        return ResponseEntity.created(URI.create("/api/v1/governance/retention-policies/" + created.getId()))
                .body(RetentionPolicyResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RetentionPolicyResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RetentionPolicyRequest req,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        RetentionPolicy entity = build(req, id, userId);
        RetentionPolicy updated = service.update(entity);
        return ResponseEntity.ok(RetentionPolicyResponse.from(updated));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<Map<String, Object>> run(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        RetentionPolicy policy = service.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("retention_policy not found: id=" + id));
        String target = policy.getTargetTable();
        AtomicInteger result = new AtomicInteger(0);
        // 수동 트리거도 GovernanceJobSupport를 통해 batch_execution_log 기록
        GovernanceJobSupport.run(batchLog, target + "_manual", "RETENTION",
                () -> {
                    int n = dispatchRun(target, dryRun);
                    result.set(n);
                    return n;
                });
        return ResponseEntity.ok(Map.of(
                "id", id,
                "targetTable", target,
                "dryRun", dryRun,
                "processed", result.get()
        ));
    }

    private int dispatchRun(String targetTable, boolean dryRun) {
        // dryRun은 personal_data_access_log Job에서만 안전하게 지원되며,
        // 다른 Job은 dryRun=true 시 0을 반환하고 실제 실행을 건너뛴다.
        if (dryRun && !"personal_data_access_log".equals(targetTable)) {
            return 0;
        }
        return switch (targetTable) {
            case "personal_data_access_log" -> personalJob.run(dryRun);
            case "audit_log"                -> auditJob.run();
            case "login_history"            -> loginJob.run();
            case "access_log"               -> accessJob.run();
            case "integration_log"          -> integrationJob.run();
            default -> throw new IllegalArgumentException("Unsupported retention target: " + targetTable);
        };
    }

    private static Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jp) {
            return jp.userId();
        }
        return null;
    }

    private static RetentionPolicy build(RetentionPolicyRequest r, Long id, Long userId) {
        return RetentionPolicy.builder()
                .id(id)
                .targetTable(r.targetTable())
                .policyType(r.policyType())
                .retentionMonths(r.retentionMonths())
                .archiveTable(r.archiveTable())
                .anonymizeColumns(r.anonymizeColumns())
                .scheduleCron(r.scheduleCron())
                .status(r.status() == null ? "ACTIVE" : r.status())
                .description(r.description())
                .updatedBy(userId)
                .build();
    }
}
