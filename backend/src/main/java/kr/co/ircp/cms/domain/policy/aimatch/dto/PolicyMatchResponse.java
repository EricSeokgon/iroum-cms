package kr.co.ircp.cms.domain.policy.aimatch.dto;

import org.apache.ibatis.type.Alias;

import java.util.List;

/**
 * 하이브리드 정책 추천 응답.
 *
 * <p>SPEC-CMS-AI-002 — {@code degraded=true}면 ML 폴백으로 규칙 단독 랭킹이다(REQ-PM-009).
 *
 * <p>MyBatis {@code type-aliases-package} 자동 스캔에서 SPEC-CMS-007
 * {@code policy.matching.dto.PolicyMatchResponse}와 단순명이 충돌하므로
 * {@code @Alias}로 별칭을 분리한다(프로젝트 ContentPageResponse 패턴 준용).
 */
@Alias("AiPolicyMatchResponse")
public record PolicyMatchResponse(
        List<PolicyMatchItem> items,
        boolean degraded) {
}
