package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 안전사고 월별 집계 Job.
 *
 * <p>SPEC-CMS-009 REQ-DATA-004 — 매월 1일 02:45 KST.
 * SPEC-CMS-006 의존 테이블 미존재 시 SKIP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyStatsMonthlyJob {

    static final String JOB_NAME = "SafetyStatsMonthlyJob";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final GovernanceStatsMapper statsMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        return run(LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(1).format(MONTH_FMT));
    }

    public int run(String targetMonth) {
        return statsMapper.upsertSafetyStatsMonthly(targetMonth);
    }

    @Scheduled(cron = "${governance.batch.safety-stats-monthly.cron:0 45 2 1 * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (statsMapper.countTable("safety_incidents") == 0) {
            GovernanceJobSupport.skip(batchLog, JOB_NAME, "STATS", "safety_incidents 테이블 미존재 (SPEC-CMS-006 미구현)");
            return;
        }
        GovernanceJobSupport.run(batchLog, JOB_NAME, "STATS", this::run);
    }
}
