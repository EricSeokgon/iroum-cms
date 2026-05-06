package kr.co.ircp.cms.domain.governance.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * 통계 수동 재계산 요청.
 *
 * <p>job: BoardStatsDailyJob | BoardStatsMonthlyJob | ContentViewStatsDailyJob |
 *        ContentViewStatsMonthlyJob | PolicyMatchStatsJob | SafetyStatsMonthlyJob
 *
 * <p>from/to는 daily Job인 경우 LocalDate 범위, monthly Job인 경우 첫 일자만 사용.
 */
public record StatsRecomputeRequest(
        @NotBlank String job,
        LocalDate from,
        LocalDate to
) {}
