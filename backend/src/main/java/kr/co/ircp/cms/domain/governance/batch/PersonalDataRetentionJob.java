package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.repository.RetentionExecutionMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.governance.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * personal_data_access_log 6개월 보존 정책 자동화.
 *
 * <p>SPEC-CMS-009 REQ-GOV-007 — 매월 1일 04:00 KST.
 * archive 테이블로 INSERT-SELECT 후 source DELETE.
 *
 * <p><b>트랜잭션 경계 (코드 리뷰 2026-05-07 #1):</b>
 * archive(ON CONFLICT DO NOTHING, 멱등) + delete를 단일 @Transactional 로 묶어
 * delete 실패 시 archive 도 함께 롤백한다. 따라서 데이터가 archive 와 source 양쪽에
 * 동시에 남아 중복(이중 기록) 되는 상태를 방지한다.
 *
 * <p>delete가 V9 {@code pda_no_delete} APPEND-ONLY 트리거로 차단되는 경우,
 * 본 트랜잭션은 RuntimeException 으로 롤백되며 {@link GovernanceJobSupport}가
 * batch_execution_log 에 status=FAILURE 로 기록한다. 운영자는 알림을 받아
 * 트리거 우회 절차를 별도로 수행해야 한다.
 */
// @MX:ANCHOR: [AUTO] PersonalDataRetentionJob — archive/delete 트랜잭션 경계 보증
// @MX:REASON: archive 멱등성(ON CONFLICT DO NOTHING) + 단일 @Transactional 로 부분 실패 시 데이터 중복 방지 (코드 리뷰 #1)
// @MX:SPEC: SPEC-CMS-009#REQ-GOV-007
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalDataRetentionJob {

    static final String JOB_NAME = "PersonalDataRetentionJob";
    private static final String TARGET = "personal_data_access_log";

    private final RetentionPolicyService policyService;
    private final RetentionExecutionMapper executionMapper;
    private final BatchExecutionLogService batchLog;

    /**
     * dryRun=true 시 archive/delete 실제 실행하지 않고 정책만 검증 (RISK-G-01 대응).
     *
     * <p>dryRun=false 시 archive + delete 가 단일 트랜잭션으로 실행된다.
     * delete 실패 시 archive 도 롤백되어 데이터 중복을 방지한다.
     */
    @Transactional
    public int run(boolean dryRun) {
        RetentionPolicy policy = policyService.findByTargetTable(TARGET)
                .orElseThrow(() -> new IllegalStateException("retention_policy not found: " + TARGET));
        if (dryRun) {
            log.info("PersonalDataRetentionJob dry-run: retentionMonths={}", policy.getRetentionMonths());
            return 0;
        }
        int archived = executionMapper.archivePersonalDataAccessLog(policy.getRetentionMonths());
        // delete 실패 시 archive 까지 롤백 — RuntimeException 을 그대로 전파하여 트랜잭션 롤백 트리거.
        // archive 쿼리는 ON CONFLICT DO NOTHING 으로 멱등이므로 다음 배치 실행에서 재시도 가능.
        try {
            executionMapper.deletePersonalDataAccessLog(policy.getRetentionMonths());
        } catch (RuntimeException e) {
            log.error("personal_data_access_log DELETE 실패 — archive 롤백 (APPEND-ONLY 트리거 가능성): {}",
                    e.getMessage());
            throw new IllegalStateException(
                    "PersonalDataRetention DELETE 실패로 archive 롤백: " + e.getMessage(), e);
        }
        return archived;
    }

    @Scheduled(cron = "${governance.batch.personal-data-retention.cron:0 0 4 1 * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "RETENTION", () -> run(false));
    }
}
