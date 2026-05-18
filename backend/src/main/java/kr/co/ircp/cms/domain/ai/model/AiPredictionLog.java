package kr.co.ircp.cms.domain.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * AI 예측 로그 엔티티.
 *
 * <p>SPEC-CMS-AI-001 — 모든 ML 추론 호출의 입력/출력/지연/상태를 적재한다.
 * JSONB 컬럼(input_features/output_result/actual_value)은 직렬화된 JSON 문자열로 다룬다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPredictionLog {
    private Long id;
    private String predictionType;   // GROWTH_STAGE / RISK_SCORE / SIMULATION
    private String modelName;
    private String modelVersion;
    private String requestRef;
    private String inputFeatures;    // JSONB (JSON 문자열)
    private String outputResult;     // JSONB (JSON 문자열)
    private BigDecimal confidence;
    private Integer latencyMs;
    private String status;           // SUCCESS / ML_ERROR / TIMEOUT / FALLBACK
    private String actualValue;      // JSONB (JSON 문자열) — 라벨링 후 실제값
    private Instant predictedAt;
    private Instant labeledAt;
}
