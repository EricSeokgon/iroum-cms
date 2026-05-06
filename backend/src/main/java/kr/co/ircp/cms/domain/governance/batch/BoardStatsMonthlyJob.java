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
 * 게시판 월별 통계 집계 Job.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001 — 매월 1일 03:00 KST.
 * board_stats_daily → board_stats_monthly 합산.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardStatsMonthlyJob {

    static final String JOB_NAME = "BoardStatsMonthlyJob";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final GovernanceStatsMapper statsMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        // 전월 = 오늘 - 1일 → 그달 (매월 1일 실행이므로 LocalDate.now().minusDays(1)이 전월의 말일)
        LocalDate prevMonth = LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
        return run(prevMonth.format(MONTH_FMT));
    }

    public int run(String targetMonth) {
        return statsMapper.upsertBoardStatsMonthly(targetMonth);
    }

    @Scheduled(cron = "${governance.batch.board-stats-monthly.cron:0 0 3 1 * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "STATS", this::run);
    }
}
