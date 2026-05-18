package kr.co.ircp.cms.domain.policy.aimatch.dto;

/**
 * 추천 상호작용 피드백 요청.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-012/013 — {@code interactionType}은
 * CLICKED/APPLIED/DISMISSED만 허용(VIEWED 거부), {@code policyId} 필수.
 */
public record PolicyFeedbackRequest(
        String sessionRef,
        String interactionType,
        Long policyId) {
}
