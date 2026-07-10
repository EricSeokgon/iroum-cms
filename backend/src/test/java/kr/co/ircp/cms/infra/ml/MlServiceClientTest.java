package kr.co.ircp.cms.infra.ml;

import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import kr.co.ircp.cms.infra.ml.dto.MlHealthResponse;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import kr.co.ircp.cms.infra.ml.dto.SimulationRequest;
import kr.co.ircp.cms.infra.ml.dto.SimulationResponse;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationRequest;
import kr.co.ircp.cms.infra.ml.dto.TagRecommendationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC-CMS-AI-001 Step 1 — MockMlServiceClient 계약 RED 테스트 (단위, Docker 불필요).
 *
 * <p>OpenAPI 계약(docs/ai-ml-service-openapi.yaml)에 정합하는 응답을 반환하는지,
 * 타임아웃 시뮬레이션이 동작하는지 검증한다. ML 요청은 PII 없는 4개 필드만 포함.
 */
// @MX:SPEC: SPEC-CMS-AI-001
@DisplayName("MockMlServiceClient 계약 단위 테스트 (SPEC-CMS-AI-001)")
class MlServiceClientTest {

    @Test
    @DisplayName("predictGrowthStage는 계약에 맞는 GrowthStageResponse를 반환한다")
    void growthStageReturnsValidResponse() {
        MockMlServiceClient client = new MockMlServiceClient();
        GrowthStageRequest request = new GrowthStageRequest("62010", 50_000_000L, 2020, 120_000_000L);

        GrowthStageResponse response = client.predictGrowthStage(request);

        assertThat(response).isNotNull();
        assertThat(response.stage())
                .isIn("SEED", "STARTUP", "GROWTH", "EXPANSION", "MATURITY");
        assertThat(response.entryProbabilities()).isNotEmpty();
        assertThat(response.confidence()).isBetween(0.0, 1.0);
        assertThat(response.modelVersion()).isNotBlank();
    }

    @Test
    @DisplayName("predictRiskScore는 계약에 맞는 RiskScoreResponse를 반환한다 (topFactors <= 3)")
    void riskScoreReturnsValidResponse() {
        MockMlServiceClient client = new MockMlServiceClient();
        RiskScoreRequest request = new RiskScoreRequest("47", 10_000_000L, 2019, 30_000_000L);

        RiskScoreResponse response = client.predictRiskScore(request);

        assertThat(response).isNotNull();
        assertThat(response.defaultProbability()).isBetween(0.0, 1.0);
        assertThat(response.riskGrade()).isIn("GREEN", "YELLOW", "ORANGE", "RED");
        assertThat(response.topFactors()).hasSizeLessThanOrEqualTo(3);
        assertThat(response.modelVersion()).isNotBlank();
    }

    @Test
    @DisplayName("predictSimulation은 계약에 맞는 SimulationResponse를 반환한다")
    void simulationReturnsValidResponse() {
        MockMlServiceClient client = new MockMlServiceClient();
        SimulationRequest request = new SimulationRequest("62010", 50_000_000L, 2020, 120_000_000L);

        SimulationResponse response = client.predictSimulation(request);

        assertThat(response).isNotNull();
        assertThat(response.projection()).isNotEmpty();
        assertThat(response.projection().get(0).year()).isPositive();
        assertThat(response.modelVersion()).isNotBlank();
    }

    @Test
    @DisplayName("health는 status와 loadedModels를 반환한다")
    void healthReturnsStatus() {
        MockMlServiceClient client = new MockMlServiceClient();

        MlHealthResponse health = client.health();

        assertThat(health).isNotNull();
        assertThat(health.status()).isNotBlank();
        assertThat(health.loadedModels()).isNotNull();
    }

    @Test
    @DisplayName("지연 시뮬레이션을 설정하면 타임아웃 예외를 던진다")
    void timeoutSimulation() {
        MockMlServiceClient client = new MockMlServiceClient();
        client.simulateTimeout(true);
        GrowthStageRequest request = new GrowthStageRequest("62010", 50_000_000L, 2020, null);

        assertThatThrownBy(() -> client.predictGrowthStage(request))
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("timeout");
    }

    // ─── SPEC-CMS-AI-004 태그 추천 (REQ-AI-TAG-004) ─────────────────────────────

    @Test
    @DisplayName("tagRecommendation은 결정적 추천 태그·점수·모델 버전을 반환한다")
    void tagRecommendationReturnsDeterministicResponse() {
        MockMlServiceClient client = new MockMlServiceClient();
        TagRecommendationRequest request =
                new TagRecommendationRequest("스마트팜 청년 창업 지원 정책 본문 내용입니다", List.of(), 5);

        TagRecommendationResponse response = client.tagRecommendation(request);

        assertThat(response).isNotNull();
        assertThat(response.recommendedTags()).isNotEmpty();
        assertThat(response.recommendedTags()).hasSizeLessThanOrEqualTo(5);
        assertThat(response.scores()).isNotEmpty();
        assertThat(response.modelVersion()).isNotBlank();
    }

    @Test
    @DisplayName("tagRecommendation 호출 시 호출 카운트가 증가한다 (캐시 hit 검증용)")
    void tagRecommendationCallCountIncrements() {
        MockMlServiceClient client = new MockMlServiceClient();
        TagRecommendationRequest request =
                new TagRecommendationRequest("스마트팜 청년 창업 지원 정책 본문 내용입니다", List.of(), 5);

        assertThat(client.tagRecommendationCallCount()).isZero();
        client.tagRecommendation(request);
        client.tagRecommendation(request);
        assertThat(client.tagRecommendationCallCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("태그 추천 타임아웃 시뮬레이션을 설정하면 MlServiceException을 던진다")
    void tagRecommendationTimeoutSimulation() {
        MockMlServiceClient client = new MockMlServiceClient();
        client.simulateTagRecommendationTimeout(true);
        TagRecommendationRequest request =
                new TagRecommendationRequest("스마트팜 청년 창업 지원 정책 본문 내용입니다", List.of(), 5);

        assertThatThrownBy(() -> client.tagRecommendation(request))
                .isInstanceOf(MlServiceException.class);
    }
}
