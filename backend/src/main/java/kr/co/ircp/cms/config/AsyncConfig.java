package kr.co.ircp.cms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
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
public class AsyncConfig {

    // @MX:WARN: [AUTO] auditExecutor — CallerRunsPolicy 사용. 큐 포화 시 호출 스레드가 직접 실행
    // @MX:REASON: 큐(500) 포화 + max(8) 달성 시 비즈니스 스레드에서 audit 로직이 실행되어 응답 지연 발생 가능
    @Bean(name = "auditExecutor")
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
}
