package kr.co.ircp.cms.domain.ai.dto;

import kr.co.ircp.cms.domain.ai.model.AiModelMetric;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 모델 성능 지표 응답 DTO.
 *
 * <p>SPEC-CMS-AI-001 — 운영자 대시보드용 지표 조회.
 */
public record AiMetricDto(
        Long id,
        String modelName,
        String predictionType,
        String aggregatePeriod,
        LocalDate periodStart,
        BigDecimal rmse,
        BigDecimal mae,
        BigDecimal accuracy,
        int sampleCount,
        boolean driftDetected
) {
    public static AiMetricDto from(AiModelMetric m) {
        return new AiMetricDto(
                m.getId(), m.getModelName(), m.getPredictionType(),
                m.getAggregatePeriod(), m.getPeriodStart(),
                m.getRmse(), m.getMae(), m.getAccuracy(),
                m.getSampleCount(), m.isDriftDetected());
    }
}
