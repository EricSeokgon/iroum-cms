package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * 정책 시맨틱 매칭 ML 응답 DTO.
 *
 * <p>SPEC-CMS-AI-002 — {@code POST /ml/v1/policy-match} 응답.
 * {@code matches}는 후보별 시맨틱 점수 + 매칭 근거. ML 서비스는 정책 DB에
 * 접근하지 않으므로 후보보다 적은 점수를 반환할 수 있다(누락 정책은 semantic=0.0 간주).
 */
public record MlPolicyMatchResponse(
        List<MlMatchItem> matches,
        String modelName,
        String modelVersion) {
}
