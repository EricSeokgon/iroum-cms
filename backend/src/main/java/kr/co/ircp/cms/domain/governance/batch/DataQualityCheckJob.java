package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.governance.service.DataQualityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 데이터 품질 검사 배치 Job.
 *
 * <p>SPEC-CMS-009 REQ-DATA-007 — 매일 06:00.
 * data_quality_rule을 모두 실행하여 data_quality_report에 적재.
 *
 * <p>Step 2: 5종 룰 모두 dispatch (NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS).
 * 위반 + severity=CRITICAL인 경우 CriticalAuditNotifier에 enqueue,
 * severity=WARN인 경우 SLF4J warn 로깅.
 */
// @MX:ANCHOR: [AUTO] DataQualityCheckJob — 거버넌스 품질 게이트의 핵심 dispatcher (5종 룰 dispatch)
// @MX:REASON: 모든 data_quality_rule이 본 Job을 통해 실행되며, 결과가 data_quality_report에 적재됨
// @MX:SPEC: SPEC-CMS-009#REQ-DATA-007
@Slf4j
@Component
@RequiredArgsConstructor
public class DataQualityCheckJob {

    static final String JOB_NAME = "DataQualityCheckJob";

    private final DataQualityMapper qualityMapper;
    private final GovernanceStatsMapper statsMapper;
    private final DataQualityService qualityService;
    private final BatchExecutionLogService batchLog;

    public int run() {
        List<DataQualityRule> rules = qualityMapper.findActiveRules();
        int processed = 0;
        for (DataQualityRule rule : rules) {
            if (statsMapper.countTable(rule.getTargetTable()) == 0) {
                log.info("품질 룰 SKIP — 테이블 미존재: rule={} table={}", rule.getId(), rule.getTargetTable());
                continue;
            }
            try {
                qualityService.runRule(rule);
                processed++;
            } catch (Exception e) {
                log.warn("품질 룰 실행 실패 rule={}: {}", rule.getId(), e.getMessage());
            }
        }
        return processed;
    }

    @Scheduled(cron = "${governance.batch.data-quality-check.cron:0 0 6 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "QUALITY", this::run);
    }
}
