package kr.co.ircp.cms.domain.policy.aimatch.dto;

import java.util.List;
import java.util.Map;

/**
 * 추천 사유 설명 객체.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-010/011 — 규칙 차원 기여({@code ruleBreakdown})와
 * 시맨틱 매칭 근거({@code matchedTerms}/{@code rationale})를 결합한다.
 * 폴백 상태에서는 {@code semanticAvailable=false}로 시맨틱 근거를 생략한다.
 */
public record PolicyMatchExplanation(
        Map<String, Object> ruleBreakdown,
        List<String> matchedTerms,
        String rationale,
        boolean semanticAvailable) {
}
