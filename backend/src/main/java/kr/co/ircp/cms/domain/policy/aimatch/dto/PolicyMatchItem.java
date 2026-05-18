package kr.co.ircp.cms.domain.policy.aimatch.dto;

/**
 * 하이브리드 추천 결과 1건.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-007/008 — {@code hybridScore = wRule*ruleScore_norm + wSemantic*semanticScore}.
 * 폴백 상태에서는 {@code semanticScore=0}이며 {@code hybridScore}는 규칙 점수만 반영한다.
 */
public record PolicyMatchItem(
        Long policyId,
        double hybridScore,
        double ruleScore,
        double semanticScore,
        PolicyMatchExplanation explanation) {
}
