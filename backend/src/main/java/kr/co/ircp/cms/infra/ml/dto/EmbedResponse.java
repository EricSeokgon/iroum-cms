package kr.co.ircp.cms.infra.ml.dto;

import java.util.List;

/**
 * 문장 임베딩 응답 (POST /ml/v1/embed).
 *
 * <p>SPEC-CMS-AI-003 REQ-RAG-002 — 384차원 임베딩 벡터.
 *
 * @param vector 384차원 float 벡터
 */
public record EmbedResponse(List<Float> vector) {
}
