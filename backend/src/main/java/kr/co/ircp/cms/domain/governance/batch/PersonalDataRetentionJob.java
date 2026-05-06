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
 * personal_data_access_log 6개월 보존 정책 자동화.
 *
 * <p>SPEC-CMS-009 REQ-GOV-007 — 매월 1일 04:00 KST.
 * archive 테이블로 INSERT-SELECT 후 source DELETE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalDataRetentionJob {

    static final String JOB_NAME = "PersonalDataRetentionJob";
    private static final String TARGET = "personal_data_access_log";

    private final RetentionPolicyService policyService;
    private final RetentionExecutionMapper executionMapper;
    private final BatchExecutionLogService batchLog;

    /** dryRun=true 시 archive/delete 실제 실행하지 않고 정책만 검증 (RISK-G-01 대응). */
    public int run(boolean dryRun) {
        RetentionPolicy policy = policyService.findByTargetTable(TARGET)
                .orElseThrow(() -> new IllegalStateException("retention_policy not found: " + TARGET));
        if (dryRun) {
            log.info("PersonalDataRetentionJob dry-run: retentionMonths={}", policy.getRetentionMonths());
            return 0;
        }
        int archived = executionMapper.archivePersonalDataAccessLog(policy.getRetentionMonths());
        // delete는 APPEND-ONLY 트리거로 차단될 수 있음 — Step 1에서는 archive까지 검증, delete는 best-effort
        try {
            executionMapper.deletePersonalDataAccessLog(policy.getRetentionMonths());
        } catch (Exception e) {
            log.warn("personal_data_access_log DELETE 실패 (APPEND-ONLY 트리거): {}", e.getMessage());
        }
        return archived;
    }

    @Scheduled(cron = "${governance.batch.personal-data-retention.cron:0 0 4 1 * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", () -> run(false));
    }
}
