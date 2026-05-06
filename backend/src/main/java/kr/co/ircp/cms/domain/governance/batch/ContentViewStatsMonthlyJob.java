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
 * 콘텐츠 월별 조회수 집계 Job.
 *
 * <p>SPEC-CMS-009 REQ-DATA-002 — 매월 1일 03:15 KST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentViewStatsMonthlyJob {

    static final String JOB_NAME = "ContentViewStatsMonthlyJob";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final GovernanceStatsMapper statsMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        return run(LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(1).format(MONTH_FMT));
    }

    public int run(String targetMonth) {
        return statsMapper.upsertContentViewStatsMonthly(targetMonth);
    }

    @Scheduled(cron = "${governance.batch.content-view-stats-monthly.cron:0 15 3 1 * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "STATS", this::run);
    }
}
