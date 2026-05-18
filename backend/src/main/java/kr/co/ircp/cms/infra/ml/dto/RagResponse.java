package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * 생성형 답변 응답 (POST /ml/v1/rag).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-004/005 — 생성 답변 + 출처 정책 관련도 + 품질 점수.
 *
 * @param answer       생성형 답변 본문
 * @param sources      출처 정책별 관련도
 * @param qualityScore 응답 품질 점수 0~100 (NULL 허용)
 */
public record RagResponse(String answer, List<Source> sources, Integer qualityScore) {

    /**
     * RAG 출처 정책 단일 항목.
     *
     * @param id        정책 ID
     * @param relevance 관련도 0.0~1.0
     */
    public record Source(long id, double relevance) {
    }
}
