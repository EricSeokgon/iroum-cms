package kr.co.ircp.cms.domain.ai.rag.dto;

/**
 * RAG 답변 만족도 피드백 요청.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-013 — queryRef로 대응 로그 행을 찾아
 * feedback(HELPFUL/UNHELPFUL)을 멱등 갱신한다(AC-RAG-004).
 *
 * @param queryRef RAG 질의 응답에서 받은 상관 UUID
 * @param feedback HELPFUL | UNHELPFUL (그 외 값은 400)
 */
public record RagFeedbackRequest(String queryRef, String feedback) {
}
