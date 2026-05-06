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
 * access_log 3개월 정리.
 *
 * <p>SPEC-CMS-009 retention_policy 시드 — 매일 04:00.
 * PARTITION DROP 자동화는 후속 SPEC. Step 1은 일괄 DELETE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogRetentionJob {

    static final String JOB_NAME = "AccessLogRetentionJob";
    private static final String TARGET = "access_log";

    private final RetentionPolicyService policyService;
    private final RetentionExecutionMapper executionMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        RetentionPolicy policy = policyService.findByTargetTable(TARGET)
                .orElseThrow(() -> new IllegalStateException("retention_policy not found: " + TARGET));
        return executionMapper.deleteAccessLog(policy.getRetentionMonths());
    }

    @Scheduled(cron = "${governance.batch.access-log-retention.cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", this::run);
    }
}
