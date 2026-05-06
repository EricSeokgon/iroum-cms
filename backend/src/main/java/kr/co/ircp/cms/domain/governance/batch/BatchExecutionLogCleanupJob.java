package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * batch_execution_log 90일 경과 정리 Job.
 *
 * <p>SPEC-CMS-009 §9.5 — batch_execution_log 보존 1년 (login_history 정책 준용),
 * 매주 일요일 05:00에 90일 경과 행 정리하여 LOG 도메인 1년 정책 충족.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchExecutionLogCleanupJob {

    static final String JOB_NAME = "BatchExecutionLogCleanupJob";
    private static final int RETENTION_DAYS = 90;

    private final BatchExecutionLogService batchLog;

    public int run() {
        return batchLog.cleanupOlderThan(RETENTION_DAYS);
    }

    @Scheduled(cron = "${governance.batch.batch-execution-log-cleanup.cron:0 0 5 * * SUN}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", this::run);
    }
}
