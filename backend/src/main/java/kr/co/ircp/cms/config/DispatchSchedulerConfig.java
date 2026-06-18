package kr.co.ircp.cms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 알림 발송 워커 전용 스케줄러 설정.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — NotificationDispatchWorker가 사용할 단일 스레드 스케줄러를 제공한다.
 * {@code @EnableScheduling}은 {@link AsyncConfig}에 이미 선언되어 있으므로 여기서는 추가하지 않는다.
 */
// @MX:NOTE: [AUTO] dispatchScheduler — 발송 워커 전용 단일 스레드. @EnableScheduling은 AsyncConfig에 존재
// @MX:SPEC: SPEC-CMS-NOTI-EXT-001
@Configuration
public class DispatchSchedulerConfig {

    @Bean(name = "dispatchScheduler")
    @ConditionalOnMissingBean(name = "dispatchScheduler")
    public TaskScheduler dispatchScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("dispatch-");
        scheduler.initialize();
        return scheduler;
    }
}
