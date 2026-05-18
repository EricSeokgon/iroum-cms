package kr.co.ircp.cms.domain.ai.rag.dto;

/**
 * RAG 답변 출처 정책.
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-005 — 답변 근거가 된 정책 ID·제목·관련도.
 *
 * @param id        정책 ID
 * @param title     정책 제목
 * @param relevance 관련도 0.0~1.0 (하이브리드 재랭킹 점수)
 */
public record RagSource(long id, String title, double relevance) {
}
