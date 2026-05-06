package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import lombok.extern.slf4j.Slf4j;

import java.util.function.IntSupplier;

/**
 * 배치 Job 공통 실행 헬퍼.
 *
 * <p>SPEC-CMS-009 §7.2 패턴: start → 본문 → success/failure.
 * 모든 14개 거버넌스 배치는 본 헬퍼를 통해 batch_execution_log를 일관되게 적재한다.
 */
// @MX:NOTE: [AUTO] 14개 배치 Job의 공통 시작/종료 패턴 wrapper
@Slf4j
public final class GovernanceJobSupport {

    private GovernanceJobSupport() {
    }

    /**
     * 배치 실행 — start → action → success/failure.
     *
     * @param batchLog BatchExecutionLogService
     * @param jobName  Job 이름
     * @param jobGroup STATS|RETENTION|QUALITY|RECOVERY
     * @param action   실제 작업 (records_processed 반환)
     */
    public static void run(BatchExecutionLogService batchLog,
                            String jobName,
                            String jobGroup,
                            IntSupplier action) {
        Long execId = batchLog.start(jobName, jobGroup);
        log.info("배치 시작: {} (execId={})", jobName, execId);
        try {
            int processed = action.getAsInt();
            batchLog.success(execId, processed);
            log.info("배치 성공: {} processed={}", jobName, processed);
        } catch (Exception e) {
            String summary = e.getClass().getSimpleName() + ": " + e.getMessage();
            batchLog.failure(execId, summary);
            log.error("배치 실패: {} — {}", jobName, summary, e);
        }
    }

    /** SKIP 결과 — 의존 SPEC 미반영 등 graceful degradation. */
    public static void skip(BatchExecutionLogService batchLog,
                             String jobName,
                             String jobGroup,
                             String reason) {
        Long execId = batchLog.start(jobName, jobGroup);
        batchLog.skip(execId, reason);
        log.info("배치 스킵: {} reason={}", jobName, reason);
    }
}
