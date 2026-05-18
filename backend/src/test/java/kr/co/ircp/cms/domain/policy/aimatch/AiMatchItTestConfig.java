package kr.co.ircp.cms.domain.policy.aimatch;

import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MockMlServiceClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * SPEC-CMS-AI-002 IT 전용 구성.
 *
 * <p>① {@link MlServiceClient}를 {@link MockMlServiceClient}({@code @Primary})로 주입한다.
 * 운영 {@code MlServiceClientImpl}은 {@code @Profile("!test")}이므로 integration 프로필에서
 * 로드되어 실제 HTTP를 시도하므로, ML 부재 시 결정적 검증을 위해 mock으로 override한다.
 *
 * <p>② {@code aiLogExecutor}를 {@link SyncTaskExecutor}로 override하여
 * {@code @Async("aiLogExecutor")} 추천/피드백 적재가 호출 스레드에서 즉시 완료되도록 한다
 * (AC-PM-004/010 결정적 검증). IntegrationAsyncConfig#auditExecutor 패턴 준용.
 */
// @MX:NOTE: [AUTO] AI-002 IT 결정적 검증 인프라 — MockMlServiceClient @Primary + aiLogExecutor 동기화
// @MX:SPEC: SPEC-CMS-AI-002
@TestConfiguration
@Profile("integration")
public class AiMatchItTestConfig {

    @Bean
    @Primary
    public MockMlServiceClient mockMlServiceClient() {
        return new MockMlServiceClient();
    }

    @Bean(name = "aiLogExecutor")
    @Primary
    public Executor aiLogExecutor() {
        return new SyncTaskExecutor();
    }
}
