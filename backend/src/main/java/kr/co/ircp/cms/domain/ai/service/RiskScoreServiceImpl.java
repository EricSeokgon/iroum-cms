package kr.co.ircp.cms.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import kr.co.ircp.cms.domain.ai.config.RiskThresholdProperties;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreQueryDto;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreResultDto;
import kr.co.ircp.cms.domain.ai.exception.AiPredictionNotFoundException;
import kr.co.ircp.cms.domain.ai.mapper.AiPredictionLogMapper;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import kr.co.ircp.cms.infra.ml.MlServiceException;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreRequest;
import kr.co.ircp.cms.infra.ml.dto.RiskScoreResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 위험도 점수 서비스 구현.
 *
 * <p>SPEC-CMS-AI-001 — ML defaultProbability를 서버 임계 설정으로 재매핑하여
 * ML이 보낸 등급을 신뢰하지 않고 RiskThresholdProperties로 일관 산출한다.
 */
// @MX:NOTE: [AUTO] RiskScoreServiceImpl — riskGrade는 ML 응답이 아닌 서버 임계로 단일 산출
// @MX:SPEC: SPEC-CMS-AI-001
@Service
public class RiskScoreServiceImpl implements RiskScoreService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoreServiceImpl.class);
    private static final String MODEL_NAME = "risk-score-model";
    private static final String TYPE = "RISK_SCORE";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MlServiceClient mlServiceClient;
    private final AiPredictionLogMapper predictionLogMapper;
    private final AiPredictionLogService aiPredictionLogService;
    private final RiskThresholdProperties thresholds;

    public RiskScoreServiceImpl(MlServiceClient mlServiceClient,
                                AiPredictionLogMapper predictionLogMapper,
                                AiPredictionLogService aiPredictionLogService,
                                RiskThresholdProperties thresholds) {
        this.mlServiceClient = mlServiceClient;
        this.predictionLogMapper = predictionLogMapper;
        this.aiPredictionLogService = aiPredictionLogService;
        this.thresholds = thresholds;
    }

    @Override
    public RiskScoreResultDto score(RiskScoreQueryDto query) {
        RiskScoreRequest request = new RiskScoreRequest(
                query.ksicCode(), query.capitalAmount(),
                query.foundingYear(), query.revenueAmount());
        long start = System.currentTimeMillis();
        try {
            RiskScoreResponse resp = mlServiceClient.predictRiskScore(request);
            // ML이 보낸 riskGrade는 무시하고 서버 임계로 재계산 (단일 진실 소스)
            String grade = thresholds.resolveGrade(resp.defaultProbability());
            List<RiskScoreResultDto.TopFactor> factors = new ArrayList<>();
            if (resp.topFactors() != null) {
                for (RiskScoreResponse.RiskFactor f : resp.topFactors()) {
                    factors.add(new RiskScoreResultDto.TopFactor(f.name(), f.contribution()));
                }
            }
            RiskScoreResultDto result = new RiskScoreResultDto(
                    resp.defaultProbability(), grade, factors, resp.modelVersion());
            aiPredictionLogService.logAsync(buildLog(query, "SUCCESS",
                    toJson(result), BigDecimal.valueOf(resp.defaultProbability()),
                    (int) (System.currentTimeMillis() - start)));
            return result;
        } catch (CallNotPermittedException | MlServiceException e) {
            log.warn("ML 위험도 호출 실패 — 폴백: {}", e.getMessage());
            aiPredictionLogService.logAsync(buildLog(query, "FALLBACK", null, null,
                    (int) (System.currentTimeMillis() - start)));
            // 보수적 폴백 — 정보 부족 시 ORANGE (주의)로 표기
            return new RiskScoreResultDto(0.5, "ORANGE", List.of(), "fallback");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RiskScoreResultDto explain(Long predictionId) {
        AiPredictionLog logEntry = predictionLogMapper.findById(predictionId)
                .orElseThrow(() -> new AiPredictionNotFoundException(predictionId));
        try {
            JsonNode root = MAPPER.readTree(logEntry.getOutputResult());
            double probability = root.path("defaultProbability").asDouble(0.0);
            String grade = root.has("riskGrade")
                    ? root.get("riskGrade").asText()
                    : thresholds.resolveGrade(probability);
            List<RiskScoreResultDto.TopFactor> factors = new ArrayList<>();
            JsonNode topFactors = root.get("topFactors");
            if (topFactors != null && topFactors.isArray()) {
                for (JsonNode f : topFactors) {
                    factors.add(new RiskScoreResultDto.TopFactor(
                            f.path("name").asText(),
                            f.path("contribution").asDouble(0.0)));
                }
            }
            return new RiskScoreResultDto(probability, grade, factors, null);
        } catch (AiPredictionNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new AiPredictionNotFoundException(predictionId);
        }
    }

    private AiPredictionLog buildLog(RiskScoreQueryDto q, String status,
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
