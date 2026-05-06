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
 * login_history 12개월 정리.
 *
 * <p>SPEC-CMS-009 REQ-GOV-009 — 매일 03:30 (월별 정리지만 일별 빠른 정리 hook도 등록).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginHistoryPurgeJob {

    static final String JOB_NAME = "LoginHistoryPurgeJob";
    private static final String TARGET = "login_history";

    private final RetentionPolicyService policyService;
    private final RetentionExecutionMapper executionMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        RetentionPolicy policy = policyService.findByTargetTable(TARGET)
                .orElseThrow(() -> new IllegalStateException("retention_policy not found: " + TARGET));
        return executionMapper.deleteLoginHistory(policy.getRetentionMonths());
    }

    @Scheduled(cron = "${governance.batch.login-history-purge.cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", this::run);
    }
}
