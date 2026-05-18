package kr.co.ircp.cms.infra.ml.dto;

/**
 * 문장 임베딩 요청 (POST /ml/v1/embed).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-002/017 — 질문 텍스트만 전송한다.
 * company_id·회원ID·세션 평문 등 식별정보를 절대 포함하지 않는다(AC-RAG-005).
 *
 * @param text 정규화된 질문 텍스트 (PII 미포함)
 */
public record EmbedRequest(String text) {
}
