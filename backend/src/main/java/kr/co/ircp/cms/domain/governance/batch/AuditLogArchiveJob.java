package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.repository.RetentionExecutionMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.governance.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * audit_log 5년 보존 정책 자동화.
 *
 * <p>SPEC-CMS-009 REQ-GOV-008 — 매월 1일 03:30 KST.
 * 6개월 경과 audit_log를 audit_log_archive로 이관 (PARTITION DETACH는 Step 2 또는 후속 SPEC).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogArchiveJob {

    static final String JOB_NAME = "AuditLogArchiveJob";
    private static final String TARGET = "audit_log";

    private final RetentionPolicyService policyService;
    private final RetentionExecutionMapper executionMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        RetentionPolicy policy = policyService.findByTargetTable(TARGET)
                .orElseThrow(() -> new IllegalStateException("retention_policy not found: " + TARGET));
        executionMapper.ensureAuditLogArchive();
        // SPEC §4.2: 5년 보존이므로 60개월 cut-off, 6개월 후 archive 이관 (Step 1은 retentionMonths 그대로 사용)
        return executionMapper.archiveAuditLog(policy.getRetentionMonths());
    }

    @Scheduled(cron = "${governance.batch.audit-log-archive.cron:0 30 3 1 * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", this::run);
    }
}
