package kr.co.ircp.cms.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.ai.dto.AiDriftAlertDto;
import kr.co.ircp.cms.domain.ai.dto.AiMetricDto;
import kr.co.ircp.cms.domain.ai.mapper.AiModelMetricMapper;
import kr.co.ircp.cms.domain.ai.mapper.AiPredictionLogMapper;
import kr.co.ircp.cms.domain.ai.mapper.AiRetrainQueueMapper;
import kr.co.ircp.cms.domain.ai.model.AiModelMetric;
import kr.co.ircp.cms.domain.ai.model.AiPredictionLog;
import kr.co.ircp.cms.domain.ai.model.AiRetrainQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 모델 성능 지표 집계 서비스 구현.
 *
 * <p>SPEC-CMS-AI-001 — 라벨링된(actual_value 존재) 예측 로그를 모아
 * RMSE/MAE/accuracy/지연 백분위를 계산하고 (modelName, predictionType,
 * aggregatePeriod, periodStart) UNIQUE upsert. 드리프트 임계:
 * accuracy &lt; 0.70 (DRIFT_ACCURACY) 또는 nRMSE &gt; 0.20 (DRIFT_ERROR).
 */
// @MX:ANCHOR: [AUTO] AiModelMetricServiceImpl.aggregate — 드리프트 자동 트리거 진입점
// @MX:REASON: AiModelMetricJob + 운영자 수동 집계 + 테스트가 호출, 재학습 큐 적재의 단일 결정점 (fan_in >= 3)
// @MX:SPEC: SPEC-CMS-AI-001
@Service
public class AiModelMetricServiceImpl implements AiModelMetricService {

    private static final Logger log = LoggerFactory.getLogger(AiModelMetricServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 드리프트 임계
    private static final double ACCURACY_THRESHOLD = 0.70;
    private static final double NRMSE_THRESHOLD = 0.20;
    // 정확도 판정 허용 오차 (|pred-actual| <= 이 값이면 적중)
    private static final double ACCURACY_TOLERANCE = 0.10;
    private static final int FETCH_LIMIT = 10_000;

    private final AiPredictionLogMapper predictionLogMapper;
    private final AiModelMetricMapper metricMapper;
    private final AiRetrainQueueMapper retrainQueueMapper;

    public AiModelMetricServiceImpl(AiPredictionLogMapper predictionLogMapper,
                                    AiModelMetricMapper metricMapper,
                                    AiRetrainQueueMapper retrainQueueMapper) {
        this.predictionLogMapper = predictionLogMapper;
        this.metricMapper = metricMapper;
        this.retrainQueueMapper = retrainQueueMapper;
    }

    @Override
    @Transactional
    public void aggregate(String predictionType, LocalDate periodStart) {
        List<AiPredictionLog> logs =
                predictionLogMapper.findByPredictionType(predictionType, FETCH_LIMIT);

        List<double[]> pairs = new ArrayList<>();   // [predicted, actual]
        List<Integer> latencies = new ArrayList<>();
        String modelName = "unknown-model";
        for (AiPredictionLog logEntry : logs) {
            if (logEntry.getModelName() != null) {
                modelName = logEntry.getModelName();
            }
            Double predicted = extractProbability(logEntry.getOutputResult());
            Double actual = extractProbability(logEntry.getActualValue());
            if (predicted != null && actual != null) {
                pairs.add(new double[]{predicted, actual});
            }
            if (logEntry.getLatencyMs() != null) {
                latencies.add(logEntry.getLatencyMs());
            }
        }

        if (pairs.isEmpty()) {
            // 라벨링된 표본 없음 — 집계 스킵 (빈 메트릭 미적재)
            log.info("AI 지표 집계 스킵 — 라벨링 표본 없음: type={}", predictionType);
            return;
        }

        double sumSq = 0.0;
        double sumAbs = 0.0;
        int hit = 0;
        double actualMin = Double.MAX_VALUE;
        double actualMax = -Double.MAX_VALUE;
        for (double[] p : pairs) {
            double err = p[0] - p[1];
            sumSq += err * err;
            sumAbs += Math.abs(err);
            if (Math.abs(err) <= ACCURACY_TOLERANCE) {
                hit++;
            }
            actualMin = Math.min(actualMin, p[1]);
            actualMax = Math.max(actualMax, p[1]);
        }
        int n = pairs.size();
        double rmse = Math.sqrt(sumSq / n);
        double mae = sumAbs / n;
        double accuracy = (double) hit / n;
        double range = Math.max(actualMax - actualMin, 1e-9);
        double nrmse = rmse / range;

        boolean driftByAccuracy = accuracy < ACCURACY_THRESHOLD;
        boolean driftByError = nrmse > NRMSE_THRESHOLD;
        boolean drift = driftByAccuracy || driftByError;

        AiModelMetric metric = AiModelMetric.builder()
                .modelName(modelName)
                .predictionType(predictionType)
                .aggregatePeriod("DAILY")
                .periodStart(periodStart)
                .rmse(scale(rmse))
                .mae(scale(mae))
                .accuracy(scale(accuracy))
                .latencyP50(percentile(latencies, 50))
                .latencyP95(percentile(latencies, 95))
                .latencyP99(percentile(latencies, 99))
                .sampleCount(n)
                .driftDetected(drift)
                .createdAt(Instant.now())
                .build();
        metricMapper.insertOrUpdate(metric);

        if (drift) {
            String reason = driftByAccuracy ? "DRIFT_ACCURACY" : "DRIFT_ERROR";
            AiRetrainQueue queue = AiRetrainQueue.builder()
                    .modelName(modelName)
                    .triggerReason(reason)
                    .triggerDetail(String.format(
                            "{\"accuracy\":%.4f,\"nrmse\":%.4f,\"sample\":%d}",
                            accuracy, nrmse, n))
                    .status("QUEUED")
                    .requestedAt(Instant.now())
                    .build();
            retrainQueueMapper.insert(queue);
            log.warn("AI 드리프트 감지 — 재학습 큐 적재: type={}, reason={}, accuracy={}",
                    predictionType, reason, accuracy);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiMetricDto> findMetrics(String predictionType, int limit) {
        // Step 1 매퍼는 type별 목록 조회를 제공하지 않으므로 예측유형별 최신 1건을 노출.
        List<AiMetricDto> result = new ArrayList<>();
        List<String> types = (predictionType == null || predictionType.isBlank())
                ? List.of("GROWTH_STAGE", "RISK_SCORE", "SIMULATION")
                : List.of(predictionType);
        for (String t : types) {
            metricMapper.findLatest(modelNameOf(t), t)
                    .map(AiMetricDto::from)
                    .ifPresent(result::add);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiDriftAlertDto> findDriftAlerts() {
        return metricMapper.findDriftDetected().stream()
                .map(AiDriftAlertDto::from)
                .toList();
    }

    private String modelNameOf(String predictionType) {
        return switch (predictionType) {
            case "GROWTH_STAGE" -> "growth-stage-clf";
            case "RISK_SCORE" -> "risk-score-model";
            case "SIMULATION" -> "simulation-model";
            default -> "unknown-model";
        };
    }

    private Double extractProbability(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode prob = root.get("defaultProbability");
            return prob != null ? prob.asDouble() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    private Integer percentile(List<Integer> values, int p) {
        if (values.isEmpty()) {
            return null;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }
}
