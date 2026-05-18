package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * 사업 위험도 점수 예측 응답 (ML 서비스).
 *
 * <p>SPEC-CMS-AI-001 — riskGrade ∈ {GREEN,YELLOW,ORANGE,RED}, topFactors 최대 3개.
 */
public record RiskScoreResponse(
        double defaultProbability,
        String riskGrade,
        List<RiskFactor> topFactors,
        String modelVersion
) {
    /** 위험 기여 요인. */
    public record RiskFactor(String name, double contribution) {
    }
}
