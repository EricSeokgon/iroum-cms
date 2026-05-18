package kr.co.ircp.cms.domain.ai.rag.dto;

/**
 * RAG 자연어 질의 요청.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-001 — 질문 텍스트만 받는다(식별정보 없음).
 * 길이 검증(빈/1000자 초과 → 400)은 서비스에서 수행한다(AC-RAG-009).
 *
 * @param question 자연어 질문 (1~1000자, 정규화 전 원문)
 */
public record RagQueryRequest(String question) {
}
