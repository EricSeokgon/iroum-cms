package kr.co.ircp.cms.infra.ml;

import kr.co.ircp.cms.infra.ml.dto.EmbedRequest;
import kr.co.ircp.cms.infra.ml.dto.EmbedResponse;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchRequest;
import kr.co.ircp.cms.infra.ml.dto.MlPolicyMatchResponse;
import kr.co.ircp.cms.infra.ml.dto.RagRequest;
import kr.co.ircp.cms.infra.ml.dto.RagResponse;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationRequest;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code test} 프로파일 전용 {@link MlServiceClient} 스텁 빈.
 *
 * <p>SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001 — 운영 {@link MlServiceClientImpl}은
 * {@code @Profile("!test")}이므로 CI({@code SPRING_PROFILES_ACTIVE=test})에서 제외된다.
 * 그 결과 ML에 의존하는 AI 서비스/컨트롤러(RiskScoreServiceImpl, GrowthStageServiceImpl,
 * SimulationServiceImpl, AiAdminController, RagQueryServiceImpl, PolicyMatchService)를
 * 적재하는 모든 {@code @SpringBootTest} 컨텍스트가 {@code NoSuchBeanDefinitionException}으로
 * 로드 실패한다. 이 빈은 그 빈 공백을 메워 컨텍스트가 정상 로드되도록 한다.
 *
 * <p>설계:
 * <ul>
 *   <li>{@code @Profile("test")} — {@code test} 프로파일에서만 활성. {@code integration}
 *       프로파일 IT(AbstractIntegrationTest, @ActiveProfiles("integration"))에서는 비활성이므로,
 *       해당 경로의 운영 {@code MlServiceClientImpl} + 기존 per-IT @TestConfiguration/@Primary
 *       override(RagItTestConfig/AiMatchItTestConfig)와 충돌하지 않는다.</li>
 *   <li>{@code @Component} — 운영 진입점 {@code @SpringBootApplication}(base package
 *       {@code kr.co.ircp.cms})의 컴포넌트 스캔 범위에 포함되어, 베이스 클래스를 상속하지 않는
 *       broad IT(IroumCmsApplicationTests, AuditLogExportIT, AuthorizationMatrixExpand2IT 등)를
 *       포함한 모든 {@code @SpringBootTest} 컨텍스트에 전역 등록된다.</li>
 *   <li>{@code src/test/java}에만 존재하므로 운영 jar에 포함되지 않는다.</li>
 *   <li>결정적 기본값을 위임받기 위해 순수 테스트 더블 {@link MockMlServiceClient}에 위임한다.
 *       기본값은 예외를 던지지 않으므로(ML 로직을 호출하지 않는 IT는 빈 존재만 필요), 컨텍스트
 *       로드용 스텁 목적을 충족한다.</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] test 프로파일 전용 MlServiceClient 스텁 — @SpringBootTest 컨텍스트 로드 복구용
// @MX:NOTE: integration 프로파일에서는 비활성(운영 Impl + per-IT @Primary override 경로 무영향)
// @MX:SPEC: SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001
@Component
@Profile("test")
public class MlServiceClientTestStub implements MlServiceClient {

    private final MockMlServiceClient delegate = new MockMlServiceClient();

    @Override
    public GrowthStageResponse predictGrowthStage(GrowthStageRequest request) {
        return delegate.predictGrowthStage(request);
    }

    @Override
    public RiskScoreResponse predictRiskScore(RiskScoreRequest request) {
        return delegate.predictRiskScore(request);
    }

    @Override
    public SimulationResponse predictSimulation(SimulationRequest request) {
        return delegate.predictSimulation(request);
    }

    @Override
    public MlPolicyMatchResponse policyMatch(MlPolicyMatchRequest request) {
        return delegate.policyMatch(request);
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        return delegate.embed(request);
    }

    @Override
    public RagResponse rag(RagRequest request) {
        return delegate.rag(request);
    }

    @Override
    public TagRecommendationResponse tagRecommendation(TagRecommendationRequest request) {
        return delegate.tagRecommendation(request);
    }

    @Override
    public MlHealthResponse health() {
        return delegate.health();
    }
}
