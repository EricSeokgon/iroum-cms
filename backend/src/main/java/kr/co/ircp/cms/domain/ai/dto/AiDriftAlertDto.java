package kr.co.ircp.cms.domain.ai.dto;

import kr.co.ircp.cms.domain.ai.model.AiModelMetric;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * AI 드리프트 경보 응답 DTO.
 *
 * <p>SPEC-CMS-AI-001 — drift_detected=true 인 지표를 경보 형태로 노출.
 */
public record AiDriftAlertDto(
        Long metricId,
        String modelName,
        String predictionType,
        LocalDate periodStart,
        BigDecimal accuracy,
        BigDecimal rmse,
        Instant detectedAt
) {
    public static AiDriftAlertDto from(AiModelMetric m) {
        return new AiDriftAlertDto(
                m.getId(), m.getModelName(), m.getPredictionType(),
                m.getPeriodStart(), m.getAccuracy(), m.getRmse(),
                m.getCreatedAt());
    }
}
