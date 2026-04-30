package kr.co.ircp.cms.domain.system.stats.batch;

import kr.co.ircp.cms.domain.system.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 월별 접속 통계 배치 Job.
 *
 * <p>REQ-SYSTEM-003-D — 매월 1일 02:00 KST 전월 daily → monthly UPSERT
 * (top_pages / top_referrers / top_browsers JSONB 포함).
 * 실패 시 RetryTemplate 3회 재시도.
 */
@Component
@RequiredArgsConstructor
public class MonthlyStatsBatchJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyStatsBatchJob.class);
    private static final int RETRY_MAX = 3;
    private static final long BACKOFF_MS = 3_600_000L;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatsService statsService;

    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Seoul")
    public void runMonthly() {
        String lastMonth = LocalDate.now().minusMonths(1).format(MONTH_FMT);
        log.info("월별 통계 배치 시작 statMonth={}", lastMonth);
        RetryTemplate retry = buildRetryTemplate();
        try {
            retry.execute((RetryCallback<Void, Exception>) ctx -> {
                statsService.aggregateMonthly(lastMonth, 1L);
                return null;
            });
            log.info("월별 통계 배치 완료 statMonth={}", lastMonth);
        } catch (Exception e) {
            log.error("월별 통계 배치 최종 실패 statMonth={} — CRITICAL", lastMonth, e);
        }
    }

    private RetryTemplate buildRetryTemplate() {
        RetryTemplate tmpl = new RetryTemplate();
        tmpl.setRetryPolicy(new SimpleRetryPolicy(RETRY_MAX));
        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(BACKOFF_MS);
        tmpl.setBackOffPolicy(backOff);
        return tmpl;
    }
}
