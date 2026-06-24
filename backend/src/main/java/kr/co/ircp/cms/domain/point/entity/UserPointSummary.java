package kr.co.ircp.cms.domain.point.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자별 포인트 합계 엔티티.
 * SPEC-CMS-POINTS-001 REQ-PNT-002~004
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPointSummary {

    private Long userId;
    private int totalPoints;
    private Instant updatedAt;
}
