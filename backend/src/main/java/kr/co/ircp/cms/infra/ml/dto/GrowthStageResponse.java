package kr.co.ircp.cms.infra.ml.dto;

import java.util.Map;

/**
 * 성장단계 예측 응답 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — stage ∈ {SEED,STARTUP,GROWTH,EXPANSION,MATURITY}.
 */
public record GrowthStageResponse(
        String stage,
        Map<String, Double> entryProbabilities,
        double confidence,
        String modelVersion
) {
}
