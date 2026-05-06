package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 게시판별 일별 통계 집계 Job.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001 — 매일 01:30 KST.
 * access_log → board_stats_daily UPSERT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardStatsDailyJob {

    static final String JOB_NAME = "BoardStatsDailyJob";

    private final GovernanceStatsMapper statsMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        return run(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1));
    }

    public int run(LocalDate targetDate) {
        return statsMapper.upsertBoardStatsDaily(targetDate);
    }

    @Scheduled(cron = "${governance.batch.board-stats-daily.cron:0 30 1 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "STATS", this::run);
    }
}
