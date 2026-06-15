package kr.co.ircp.cms.infra.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 태그 추천 응답 (POST /ml/v1/tag-recommend).
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-002 — 추천 태그 목록·신뢰도 점수 맵·모델 버전.
 * snake_case 역직렬화로 Python FastAPI 계약에 정합한다.
 *
 * @param recommendedTags 순서 보존 추천 태그 배열
 * @param scores          태그별 신뢰도 점수 맵 {"태그1": 0.92, ...}
 * @param modelVersion    ML 모델 버전 (NULL 허용)
 */
public record TagRecommendationResponse(
        @JsonProperty("recommended_tags") List<String> recommendedTags,
        @JsonProperty("scores") Map<String, Double> scores,
        @JsonProperty("model_version") String modelVersion) {
}
