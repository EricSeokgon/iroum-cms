package kr.co.ircp.cms.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * IT 전용 비동기 실행기 설정 (auditExecutor override).
 *
 * <p>SPEC-CMS-SECURITY-PII-FOLLOWUP-001 REQ-PII-FU-001 — IT 환경에서
 * {@code @Async("auditExecutor")} 호출이 호출 스레드에서 즉시 동기 실행되도록
 * 운영 {@link AsyncConfig#auditExecutor()} Bean을 {@link SyncTaskExecutor}로 override한다.
 *
 * <p>적용 범위:
 * <ul>
 *   <li>{@code @Profile("integration")} 분기로 {@link AbstractIntegrationTest}의
 *       {@code @ActiveProfiles("integration")} 환경에서만 활성화된다.</li>
 *   <li>운영(default profile)에서는 본 클래스가 로드되지 않으므로
 *       {@link AsyncConfig#auditExecutor()}의 ThreadPoolTaskExecutor 비동기 실행이 그대로 유지된다.</li>
 * </ul>
 *
 * <p>도입 배경 (PII-002 후속):
 * <ul>
 *   <li>SPEC-CMS-SECURITY-PII-002 RUN 1차에서 {@code recordBulk} 메서드에 {@code @Async("auditExecutor")}
 *       + {@code @Transactional(propagation = REQUIRES_NEW)}를 적용한 결과, IT 클래스 레벨
 *       {@code @Transactional} 격리와 별도 audit 트랜잭션이 분리되어 IT 검증 측에서
 *       Awaitility 2초 polling 후에도 audit row를 발견하지 못하는 사례가 발생.</li>
 *   <li>본 IT-only override로 비동기 분기를 제거하여 결정적 검증 인프라를 제공한다.
 *       비동기 동작 자체의 회귀는 단위 테스트(PersonalDataAccessLogServiceImplTest)가 보장한다.</li>
 * </ul>
 *
 * <p>Bean override 정책: {@code @Bean(name = "auditExecutor")} 이름 일치 + {@code @Primary}로
 * 동일 이름 Bean이 둘 이상 존재할 가능성을 차단한다. {@code spring.main.allow-bean-definition-overriding}
 * 설정 의존성 없이 profile 분기로 override가 결정된다.
 */
// @MX:NOTE: [AUTO] IntegrationAsyncConfig — IT 환경(@Profile("integration"))에서 auditExecutor Bean을 SyncTaskExecutor로 override
// @MX:SPEC: SPEC-CMS-SECURITY-PII-FOLLOWUP-001 / REQ-PII-FU-001 — @Async("auditExecutor") IT 결정적 검증 인프라
@TestConfiguration
@Profile("integration")
public class IntegrationAsyncConfig {

    /**
     * IT 전용 auditExecutor — 호출 스레드 동기 실행.
     *
     * <p>운영 {@link AsyncConfig#auditExecutor()}의 ThreadPoolTaskExecutor를 대체하여
     * {@code @Async("auditExecutor")} 메서드가 호출 스레드에서 즉시 완료되도록 한다.
     *
     * @return {@link SyncTaskExecutor} 인스턴스
     */
    @Bean(name = "auditExecutor")
    @Primary
    public Executor auditExecutor() {
        return new SyncTaskExecutor();
    }
}
