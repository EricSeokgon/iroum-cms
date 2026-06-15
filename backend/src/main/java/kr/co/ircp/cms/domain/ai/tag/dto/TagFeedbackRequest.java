package kr.co.ircp.cms.domain.ai.tag.dto;

/**
 * 태그 추천 채택/거부 피드백 요청 (POST /api/v1/ai/tag-recommend/feedback).
 *
 * <p>SPEC-CMS-AI-004 REQ-AI-TAG-012 — 채택/거부 이벤트를 향후 모델 파인튜닝 입력으로 보존한다.
 * 본문(content)은 추천 시점과 동일 해시 키 산출에만 사용되며 평문 저장하지 않는다.
 *
 * @param content     추천 입력 본문 텍스트 (content_hash 산출용)
 * @param contentType 콘텐츠 유형 POST / QNA (NULL 시 POST 기본)
 * @param eventType   ACCEPTED / REJECTED
 * @param tagValue    채택/거부 대상 태그
 */
public record TagFeedbackRequest(String content, String contentType, String eventType,
                                 String tagValue) {
}
