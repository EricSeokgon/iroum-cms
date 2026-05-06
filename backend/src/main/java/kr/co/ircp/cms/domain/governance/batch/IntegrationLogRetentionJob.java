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
 * integration_log 6개월 보존 정책 자동화.
 *
 * <p>SPEC-CMS-009 retention_policy 시드 — 매일 04:30.
 * integration_log 테이블이 SPEC-CMS-005 후속 마이그레이션에서 추가되므로,
 * 미존재 시 graceful skip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationLogRetentionJob {

    static final String JOB_NAME = "IntegrationLogRetentionJob";
    private static final String TARGET = "integration_log";

    private final RetentionPolicyService policyService;
    private final RetentionExecutionMapper executionMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        if (executionMapper.integrationLogExists() == 0) {
            log.info("integration_log 테이블 미존재 — SKIP");
            return 0;
        }
        RetentionPolicy policy = policyService.findByTargetTable(TARGET)
                .orElseThrow(() -> new IllegalStateException("retention_policy not found: " + TARGET));
        int archived = executionMapper.archiveIntegrationLog(policy.getRetentionMonths());
        executionMapper.deleteIntegrationLog(policy.getRetentionMonths());
        return archived;
    }

    @Scheduled(cron = "${governance.batch.integration-log-retention.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (executionMapper.integrationLogExists() == 0) {
            GovernanceJobSupport.skip(batchLog, JOB_NAME, "RETENTION", "integration_log 미존재");
            return;
        }
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", this::run);
    }
}
