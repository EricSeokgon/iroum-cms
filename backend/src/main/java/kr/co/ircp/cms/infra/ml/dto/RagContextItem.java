package kr.co.ircp.cms.infra.ml.dto;

/**
 * RAG 생성 컨텍스트 단일 정책 문서 (POST /ml/v1/rag 입력).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-004/017 — 정책 문서(ID·제목·본문)만 전송한다.
 * 사용자 식별정보를 포함하지 않는다.
 *
 * @param id      정책 ID
 * @param title   정책 제목
 * @param content 정책 본문(컨텍스트)
 */
public record RagContextItem(long id, String title, String content) {
}
