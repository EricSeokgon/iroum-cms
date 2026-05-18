package kr.co.ircp.cms.domain.ai.dto;

import java.util.List;

/**
 * 위험도 점수 결과 DTO.
 *
 * <p>SPEC-CMS-AI-001 — riskGrade ∈ {GREEN,YELLOW,ORANGE,RED}.
 * 등급은 {@code ai.risk.thresholds} 설정 기반으로 서버에서 재계산한다.
 */
public record RiskScoreResultDto(
        double defaultProbability,
        String riskGrade,
        List<TopFactor> topFactors,
        String modelVersion
) {
    /** 위험 기여 요인. */
    public record TopFactor(String name, double contribution) {
    }
}
