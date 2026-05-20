package kr.co.ircp.cms.infra.ml;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * RestTemplate 기반 ML 서비스 클라이언트 구현.
 *
 * <p>SPEC-CMS-AI-001 — 엔드포인트별 타임아웃 분리, Resilience4j Circuit Breaker(ml-service) 적용.
 * 실패 시 폴백 메서드가 {@link MlServiceException}을 던져 호출부 FALLBACK 처리로 위임한다.
 *
 * <p>{@code @Profile("!test")} — 테스트 컨텍스트에서는 {@link MockMlServiceClient}를 사용한다.
 */
// @MX:WARN: [AUTO] 엔드포인트별 RestTemplate 4개 — 타임아웃 격리. 폴백은 예외 재던지기로 단순화
// @MX:REASON: risk-score(500ms)는 동기 응답 경로라 타임아웃이 짧음. growth/simulation(3000ms)과 풀 분리 필요
// @MX:SPEC: SPEC-CMS-AI-001
@Component
@Profile("!test")
public class MlServiceClientImpl implements MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceClientImpl.class);
    private static final String CB_NAME = "ml-service";

    private final String baseUrl;
    private final RestTemplate growthStageRt;
    private final RestTemplate riskScoreRt;
    private final RestTemplate simulationRt;
    private final RestTemplate policyMatchRt;
    private final RestTemplate embedRt;
    private final RestTemplate ragRt;
    private final RestTemplate healthRt;

    public MlServiceClientImpl(
            RestTemplateBuilder builder,
            @Value("${ml.service.base-url}") String baseUrl,
            @Value("${ml.service.timeout.growth-stage-ms:3000}") long growthStageMs,
            @Value("${ml.service.timeout.risk-score-ms:500}") long riskScoreMs,
            @Value("${ml.service.timeout.simulation-ms:3000}") long simulationMs,
            @Value("${ml.service.timeout.policy-match-ms:3000}") long policyMatchMs,
            @Value("${ml.service.timeout.embed-ms:1500}") long embedMs,
            @Value("${ml.service.timeout.rag-ms:5000}") long ragMs,
            @Value("${ml.service.timeout.health-ms:1000}") long healthMs) {
        this.baseUrl = baseUrl;
        this.growthStageRt = rt(builder, growthStageMs);
        this.riskScoreRt = rt(builder, riskScoreMs);
        this.simulationRt = rt(builder, simulationMs);
        this.policyMatchRt = rt(builder, policyMatchMs);
        this.embedRt = rt(builder, embedMs);
        this.ragRt = rt(builder, ragMs);
        this.healthRt = rt(builder, healthMs);
    }

    // SimpleClientHttpRequestFactory를 명시적으로 지정 — Spring Boot 3.5.x에서 RestTemplateBuilder가
    // JdkClientHttpRequestFactory(java.net.http.HttpClient)를 기본 사용할 때 body가 null로 전송되는 문제 우회
    private RestTemplate rt(RestTemplateBuilder builder, long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        return builder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(timeout);
                    factory.setReadTimeout(timeout);
                    return factory;
                })
                .build();
    }

    // Content-Type: application/json 헤더를 명시적으로 설정 — Python Pydantic extra="forbid" 환경에서
    // RestTemplate이 body 없이 요청을 보내는 경우 422 오류가 발생하므로 HttpEntity로 감쌈
    private static <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackGrowthStage")
    public GrowthStageResponse predictGrowthStage(GrowthStageRequest request) {
        try {
            return growthStageRt.postForObject(
                    baseUrl + "/ml/v1/growth-stage", jsonEntity(request), GrowthStageResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("growth-stage prediction failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackRiskScore")
    public RiskScoreResponse predictRiskScore(RiskScoreRequest request) {
        try {
            return riskScoreRt.postForObject(
                    baseUrl + "/ml/v1/risk-score", jsonEntity(request), RiskScoreResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("risk-score prediction failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackSimulation")
    public SimulationResponse predictSimulation(SimulationRequest request) {
        try {
            return simulationRt.postForObject(
                    baseUrl + "/ml/v1/simulation", jsonEntity(request), SimulationResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("simulation failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackPolicyMatch")
    public MlPolicyMatchResponse policyMatch(MlPolicyMatchRequest request) {
        try {
            return policyMatchRt.postForObject(
                    baseUrl + "/ml/v1/policy-match", jsonEntity(request), MlPolicyMatchResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("policy-match failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackEmbed")
    public EmbedResponse embed(EmbedRequest request) {
        try {
            return embedRt.postForObject(
                    baseUrl + "/ml/v1/embed", jsonEntity(request), EmbedResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("embed failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackRag")
    public RagResponse rag(RagRequest request) {
        try {
            return ragRt.postForObject(
                    baseUrl + "/ml/v1/rag", jsonEntity(request), RagResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("rag generation failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackHealth")
    public MlHealthResponse health() {
        try {
            return healthRt.getForObject(baseUrl + "/ml/v1/health", MlHealthResponse.class);
        } catch (RestClientException e) {
            throw new MlServiceException("ml-service health check failed", e);
        }
    }

    // ─── Circuit Breaker 폴백: 회로 OPEN 또는 예외 시 호출 ──────────────────────

    // @MX:WARN: [AUTO] Resilience4j CircuitBreaker "ml-service" — 10회 슬라이딩 윈도우, 50% 실패율시 OPEN(30s)
    // @MX:REASON: ML 서비스 장애 시 fallback 응답 반환하여 CMS 가용성 보호
    // @MX:SPEC: SPEC-CMS-AI-001
    @SuppressWarnings("unused")
    private GrowthStageResponse fallbackGrowthStage(GrowthStageRequest request, Throwable t) {
        log.warn("ml-service growth-stage fallback: {}", t.getMessage());
        throw new MlServiceException("ml-service unavailable (growth-stage fallback)", t);
    }

    @SuppressWarnings("unused")
    private RiskScoreResponse fallbackRiskScore(RiskScoreRequest request, Throwable t) {
        log.warn("ml-service risk-score fallback: {}", t.getMessage());
        throw new MlServiceException("ml-service unavailable (risk-score fallback)", t);
    }

    @SuppressWarnings("unused")
    private SimulationResponse fallbackSimulation(SimulationRequest request, Throwable t) {
        log.warn("ml-service simulation fallback: {}", t.getMessage());
        throw new MlServiceException("ml-service unavailable (simulation fallback)", t);
    }

    @SuppressWarnings("unused")
    private MlPolicyMatchResponse fallbackPolicyMatch(MlPolicyMatchRequest request, Throwable t) {
        log.warn("ml-service policy-match fallback: {}", t.getMessage());
        throw new MlServiceException("ml-service unavailable (policy-match fallback)", t);
    }

    @SuppressWarnings("unused")
    private EmbedResponse fallbackEmbed(EmbedRequest request, Throwable t) {
        log.warn("ml-service embed fallback: {}", t.getMessage());
        throw new MlServiceException("ml-service unavailable (embed fallback)", t);
    }

    @SuppressWarnings("unused")
    private RagResponse fallbackRag(RagRequest request, Throwable t) {
        log.warn("ml-service rag fallback: {}", t.getMessage());
        throw new MlServiceException("ml-service unavailable (rag fallback)", t);
    }

    @SuppressWarnings("unused")
    private MlHealthResponse fallbackHealth(Throwable t) {
        log.warn("ml-service health fallback: {}", t.getMessage());
        return new MlHealthResponse("DOWN", List.<String>of());
    }
}
