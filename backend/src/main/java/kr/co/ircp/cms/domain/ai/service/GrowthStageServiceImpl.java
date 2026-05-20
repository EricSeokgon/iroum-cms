package kr.co.ircp.cms.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import kr.co.ircp.cms.domain.ai.dto.GrowthStageQueryDto;
import kr.co.ircp.cms.domain.ai.dto.GrowthStageResultDto;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageRequest;
import kr.co.ircp.cms.infra.ml.dto.GrowthStageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 성장단계 예측 서비스 구현.
 *
 * <p>SPEC-CMS-AI-001 — @Cacheable(aiGrowthStage)는 컨트롤러 레이어에서 적용.
 * 본 구현은 ML 호출 + 비동기 로그 + 폴백을 담당한다.
 *
 * <p>폴백 시맨틱:
 * <ul>
 *   <li>{@link MlServiceException} (타임아웃/5xx) → status=FALLBACK 로그 + UNKNOWN 응답</li>
 *   <li>{@link CallNotPermittedException} (서킷 OPEN) → ML 미호출, 즉시 FALLBACK</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] GrowthStageServiceImpl — ML 실패를 흡수하고 항상 결정적 결과를 반환 (가용성 우선)
// @MX:SPEC: SPEC-CMS-AI-001
@Service
public class GrowthStageServiceImpl implements GrowthStageService {

    private static final Logger log = LoggerFactory.getLogger(GrowthStageServiceImpl.class);
    private static final String MODEL_NAME = "growth-stage-clf";
    private static final String TYPE = "GROWTH_STAGE";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MlServiceClient mlServiceClient;
    private final AiPredictionLogService aiPredictionLogService;

    public GrowthStageServiceImpl(MlServiceClient mlServiceClient,
                                  AiPredictionLogService aiPredictionLogService) {
        this.mlServiceClient = mlServiceClient;
        this.aiPredictionLogService = aiPredictionLogService;
    }

    // @MX:NOTE: [AUTO] @Cacheable(aiGrowthStage)는 컨트롤러 레이어 적용 — 캐시 히트 시 본 메서드/비동기 로그 모두 생략
    // @MX:SPEC: SPEC-CMS-AI-001
    @Override
    public GrowthStageResultDto predict(GrowthStageQueryDto query) {
        GrowthStageRequest request = new GrowthStageRequest(
                query.ksicCode(), query.capitalAmount(),
                query.foundingYear(), query.revenueAmount());
        long start = System.currentTimeMillis();
        try {
            GrowthStageResponse resp = mlServiceClient.predictGrowthStage(request);
            int latency = (int) (System.currentTimeMillis() - start);
            aiPredictionLogService.logAsync(buildLog(query, "SUCCESS",
                    toJson(resp), BigDecimal.valueOf(resp.confidence()), latency));
            return new GrowthStageResultDto(
                    resp.stage(), resp.entryProbabilities(),
                    resp.confidence(), resp.modelVersion(), false);
        } catch (CallNotPermittedException e) {
            // 서킷 OPEN — ML 미호출, 즉시 폴백
            log.warn("ML 성장단계 서킷 OPEN — 즉시 폴백");
            aiPredictionLogService.logAsync(buildLog(query, "FALLBACK", null, null,
                    (int) (System.currentTimeMillis() - start)));
            return fallback();
        } catch (MlServiceException e) {
            // 타임아웃/네트워크/5xx — 폴백
            log.warn("ML 성장단계 호출 실패 — 폴백 처리: {}", e.getMessage());
            aiPredictionLogService.logAsync(buildLog(query, "FALLBACK", null, null,
                    (int) (System.currentTimeMillis() - start)));
            return fallback();
        }
    }

    private GrowthStageResultDto fallback() {
        return new GrowthStageResultDto("UNKNOWN", Map.of(), 0.0, "fallback", true);
    }

    private AiPredictionLog buildLog(GrowthStageQueryDto q, String status,
                                     String outputJson, BigDecimal confidence, int latencyMs) {
        return AiPredictionLog.builder()
                .predictionType(TYPE)
                .modelName(MODEL_NAME)
                .modelVersion("rule-v1.0.0")
                .inputFeatures(toJson(q))
                .outputResult(outputJson)
                .confidence(confidence)
                .latencyMs(latencyMs)
                .status(status)
                .predictedAt(Instant.now())
                .build();
    }

    private static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
