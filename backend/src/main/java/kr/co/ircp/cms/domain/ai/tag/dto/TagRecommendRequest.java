package kr.co.ircp.cms.domain.ai.tag.dto;

import java.util.List;

/**
 * 태그 추천 요청 (POST /api/v1/ai/tag-recommend).
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-006/007 — 본문·기존 선택 태그·콘텐츠 유형만 받는다.
 * 작성자 식별정보(PII)는 포함하지 않으며, 세션 식별은 컨트롤러가 remoteAddr를 전달해
 * 서비스에서 즉시 SHA-256 해시한다(REQ-AI-TAG-013).
 *
 * @param content      추천 입력 본문 텍스트
 * @param existingTags 작성자가 이미 선택한 태그 목록 (중복 제외용, NULL 허용)
 * @param contentType  콘텐츠 유형 POST / QNA (NULL 시 POST 기본)
 */
public record TagRecommendRequest(String content, List<String> existingTags, String contentType) {
}
