package kr.co.ircp.cms.domain.ai.tag.dto;

import java.util.List;

/**
 * 태그 추천 응답 (POST /api/v1/ai/tag-recommend).
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-006/009 — camelCase 추천 태그 배열을 반환한다.
 * ML 장애·짧은 본문 시 빈 배열(HTTP 200)을 반환하여 글쓰기 흐름을 보호한다(그레이스풀 폴백).
 *
 * @param recommendedTags 추천 태그 배열 (최대 5개, 빈 배열 허용)
 */
public record TagRecommendResponse(List<String> recommendedTags) {

    /** 빈 추천 결과 — 짧은 본문·ML 장애 시 그레이스풀 폴백 응답(HTTP 200). */
    public static TagRecommendResponse empty() {
        return new TagRecommendResponse(List.of());
    }
}
