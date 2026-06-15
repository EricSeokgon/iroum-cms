package kr.co.ircp.cms.infra.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 태그 추천 요청 (POST /ml/v1/tag-recommend).
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-002/NFR-003 — 본문 텍스트와 기존 선택 태그만 전송한다.
 * 작성자 식별정보(회원ID·세션 평문 등 PII)를 절대 포함하지 않는다(AC-AI-TAG-007/NFR-003).
 * snake_case 직렬화로 Python FastAPI 계약에 정합한다.
 *
 * @param content      추천 입력 본문 텍스트 (PII 미포함)
 * @param existingTags 작성자가 이미 선택한 태그 목록 (중복 제외용)
 * @param topK         반환받을 최대 추천 태그 수
 */
public record TagRecommendationRequest(
        @JsonProperty("content") String content,
        @JsonProperty("existing_tags") List<String> existingTags,
        @JsonProperty("top_k") Integer topK) {
}
