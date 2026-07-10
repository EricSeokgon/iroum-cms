package kr.co.ircp.cms.domain.point.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 포인트 정책 변경 요청.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-005 — 관리자 정책 변경. 포인트 값은 음수 불가(적립 전용).
 */
public record PointPolicyUpdateRequest(
        @NotNull Boolean enabled,
        @NotNull @Min(0) Integer postPoints,
        @NotNull @Min(0) Integer commentPoints,
        @NotNull @Min(0) Integer likePoints
) {
}
