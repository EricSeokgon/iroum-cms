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

/**
 * 일별 접속 통계 배치 Job.
 *
 * <p>REQ-SYSTEM-002-D — 매일 01:00 KST 전일 access_log를 집계하여
 * access_stat_daily UPSERT. 실패 시 RetryTemplate 3회 재시도.
 */
// @MX:WARN: [AUTO] @Scheduled 단일 스레드 — 배치 중복 실행 방지 보장
// @MX:REASON: Spring 기본 TaskScheduler는 단일 스레드; 02:00 배치와 겹칠 경우 큐잉됨
@Component
@RequiredArgsConstructor
public class DailyStatsBatchJob {

    private static final Logger log = LoggerFactory.getLogger(DailyStatsBatchJob.class);
    private static final int RETRY_MAX = 3;
    /** 재시도 간격 1시간 (3,600,000 ms) */
    private static final long BACKOFF_MS = 3_600_000L;

    private final StatsService statsService;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("일별 통계 배치 시작 targetDate={}", yesterday);
        RetryTemplate retry = buildRetryTemplate();
        try {
            retry.execute((RetryCallback<Void, Exception>) ctx -> {
                statsService.aggregateDaily(yesterday, 1L);
                return null;
            });
            log.info("일별 통계 배치 완료 targetDate={}", yesterday);
        } catch (Exception e) {
            log.error("일별 통계 배치 최종 실패 targetDate={} — CRITICAL", yesterday, e);
            // SPEC: 실패 시 audit_log severity=CRITICAL 기록 (추후 @AuditLog 연동)
        }
    }

    private RetryTemplate buildRetryTemplate() {
        RetryTemplate tmpl = new RetryTemplate();
        SimpleRetryPolicy policy = new SimpleRetryPolicy(RETRY_MAX);
        tmpl.setRetryPolicy(policy);
        FixedBackOffPolicy backOff = new FixedBackOffPolicy();
        backOff.setBackOffPeriod(BACKOFF_MS);
        tmpl.setBackOffPolicy(backOff);
        return tmpl;
    }
}
