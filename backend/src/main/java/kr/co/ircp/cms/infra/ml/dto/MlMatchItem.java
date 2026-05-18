package kr.co.ircp.cms.infra.ml.dto;

/**
 * ML 정책별 시맨틱 매칭 점수 1건.
 *
 * <p>SPEC-CMS-AI-002 — {@code semanticScore}는 0.0~1.0 범위.
 */
public record MlMatchItem(
        Long policyId,
        double semanticScore,
        MlMatchExplanation explanation) {
}
