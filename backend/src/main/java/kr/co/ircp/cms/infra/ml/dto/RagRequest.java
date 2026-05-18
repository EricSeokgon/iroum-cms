package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * 생성형 답변 요청 (POST /ml/v1/rag).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-004/017 — 질문 텍스트와 검색된 정책 컨텍스트만 전송한다.
 * company_id·회원ID·세션 평문 등 식별정보를 절대 포함하지 않는다(AC-RAG-005).
 *
 * @param question 정규화된 질문 텍스트 (PII 미포함)
 * @param contexts 상위 K개 정책 컨텍스트
 */
public record RagRequest(String question, List<RagContextItem> contexts) {
}
