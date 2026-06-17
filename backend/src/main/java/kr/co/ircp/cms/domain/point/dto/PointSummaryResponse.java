package kr.co.ircp.cms.domain.point.dto;

import java.time.Instant;

/**
 * 사용자 포인트 누적 총액 응답.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-006 — 사용자 본인 총액 조회.
 */
public record PointSummaryResponse(
        Long userId,
        long totalPoints,
        Instant updatedAt
) {
}
