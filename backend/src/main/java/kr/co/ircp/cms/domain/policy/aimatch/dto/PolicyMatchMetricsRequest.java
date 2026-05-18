package kr.co.ircp.cms.domain.policy.aimatch.dto;

import java.time.LocalDate;

/**
 * 추천 품질 지표 조회 요청.
 *
 * <p>SPEC-CMS-AI-002 REQ-PM-015 — {@code period}는 DAILY/WEEKLY/MONTHLY.
 * {@code from}/{@code to}는 nullable(미지정 시 전체 기간).
 */
public record PolicyMatchMetricsRequest(
        String period,
        LocalDate from,
        LocalDate to) {
}
