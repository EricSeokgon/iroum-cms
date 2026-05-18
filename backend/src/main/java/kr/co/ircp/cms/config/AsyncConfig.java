package kr.co.ircp.cms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 실행기 설정.
 *
 * <p>SPEC-CMS-005 §7 — 감사 로그 비동기 적재를 위한 전용 스레드 풀.
 * 감사 로그 실패가 비즈니스 로직에 영향을 주지 않도록 분리한다.
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableRetry
public class AsyncConfig {

    // @MX:WARN: [AUTO] auditExecutor — CallerRunsPolicy 사용. 큐 포화 시 호출 스레드가 직접 실행
    // @MX:REASON: 큐(500) 포화 + max(8) 달성 시 비즈니스 스레드에서 audit 로직이 실행되어 응답 지연 발생 가능
    @Bean(name = "auditExecutor")
    @ConditionalOnMissingBean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        // 큐 포화 시 callerRuns — 감사 로그 유실보다 지연이 낫다
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 접속 로그 비동기 저장 전용 실행기.
     * REQ-SYSTEM-001-D: AccessLogFilter → AccessLogService 비동기 저장
     * auditExecutor 재사용 대신 분리하여 접속 로그 폭증이 감사 로그에 영향을 주지 않도록 설계.
     */
    @Bean(name = "accessLogExecutor")
    public Executor accessLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("access-log-");
        // 큐 포화 시 조용히 폐기 (접속 로그 유실보다 응답 영향을 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 검색 로그 비동기 저장 전용 실행기.
     * SPEC-CMS-010 REQ-SEARCH-008: SearchLogAsyncService → SearchLogMapper.insert 비동기 적재.
     * 검색 응답 지연을 막기 위해 분리. 큐 포화 시 DiscardPolicy로 로그 유실 허용.
     */
    @Bean(name = "searchLogExecutor")
    public Executor searchLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("search-log-");
        // 큐 포화 시 조용히 폐기 (검색 응답 영향 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * AI 예측 로그 비동기 적재 전용 실행기.
     * SPEC-CMS-AI-001 — AiPredictionLogService#logAsync → AiPredictionLogMapper.insert.
     * ML 응답 경로(예측 결과 반환)에 로그 적재 지연/실패가 영향을 주지 않도록 분리.
     * 큐 포화 시 DiscardPolicy로 로그 유실 허용(예측 응답 우선).
     */
    @Bean(name = "aiLogExecutor")
    public Executor aiLogExecutor(
            @Value("${ai.async.core-pool-size:2}") int corePoolSize,
            @Value("${ai.async.max-pool-size:4}") int maxPoolSize,
            @Value("${ai.async.queue-capacity:500}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
