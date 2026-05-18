package kr.co.ircp.cms.domain.policy.aimatch.dto;

/**
 * 추천 품질 지표 응답.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-015/016 — CTR(클릭 보유 세션 / VIEWED 세션),
 * 신청 전환율(APPLIED 세션 / VIEWED 세션), 추천 커버리지(추천 등장 정책 수 / 활성 정책 총수).
 */
public record PolicyMatchMetricsResponse(
        String period,
        double ctr,
        double conversionRate,
        double coverage,
        long totalViewed,
        long totalClicked,
        long totalApplied) {
}
