package kr.co.ircp.cms.domain.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * AI 모델 성능 지표 엔티티.
 *
 * <p>SPEC-CMS-AI-001 — 모델/예측유형/집계주기/기간 단위 성능 집계.
 * (modelName, predictionType, aggregatePeriod, periodStart)가 UNIQUE이며 upsert 대상이다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelMetric {
    private Long id;
    private String modelName;
    private String predictionType;   // GROWTH_STAGE / RISK_SCORE / SIMULATION
    private String aggregatePeriod;  // DAILY / WEEKLY / MONTHLY
    private LocalDate periodStart;
    private BigDecimal rmse;
    private BigDecimal mae;
    private BigDecimal accuracy;
    private Integer latencyP50;
    private Integer latencyP95;
    private Integer latencyP99;
    private int sampleCount;
    private boolean driftDetected;
    private Instant createdAt;
}
