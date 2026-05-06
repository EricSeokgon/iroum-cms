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
 * 콘텐츠 일별 조회수 집계 Job.
 *
 * <p>SPEC-CMS-009 REQ-DATA-002 — 매일 01:45 KST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentViewStatsDailyJob {

    static final String JOB_NAME = "ContentViewStatsDailyJob";

    private final GovernanceStatsMapper statsMapper;
    private final BatchExecutionLogService batchLog;

    public int run() {
        return run(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1));
    }

    public int run(LocalDate targetDate) {
        return statsMapper.upsertContentViewStatsDaily(targetDate);
    }

    @Scheduled(cron = "${governance.batch.content-view-stats-daily.cron:0 45 1 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        GovernanceJobSupport.run(batchLog, JOB_NAME, "STATS", this::run);
    }
}
