package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * ML 시맨틱 매칭 근거 (매칭 토큰 + 설명 문구).
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-010 — 추천 사유의 시맨틱 부분.
 */
public record MlMatchExplanation(
        List<String> matchedTerms,
        String rationale) {
}
