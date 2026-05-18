package kr.co.ircp.cms.domain.policy.aimatch.dto;

import java.util.Map;

/**
 * 하이브리드 정책 추천 요청.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-001 — 비회원은 본문 프로필 전달, 회원은
 * 인증 컨텍스트의 DB 프로필이 우선 적용된다(REQ-PM-006). {@code topK}는
 * [1, 50]로 클램프되며 미지정 시 기본 10(REQ-PM-002).
 */
public record PolicyMatchRequest(
        Map<String, Object> companyProfile,
        String queryText,
        Integer topK) {
}
