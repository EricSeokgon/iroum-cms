package kr.co.ircp.cms.domain.ai.dto;

import java.util.Map;

/**
 * 성장단계 예측 결과 DTO.
 *
 * <p>SPEC-CMS-AI-001 — {@code fallback=true} 이면 ML 실패로 인한 대체 응답.
 */
public record GrowthStageResultDto(
        String stage,
        Map<String, Double> entryProbabilities,
        double confidence,
        String modelVersion,
        boolean fallback
) {
}
