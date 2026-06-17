package kr.co.ircp.cms.domain.point.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자별 누적 포인트 요약 엔티티 (비정규화).
 *
 * <p>SPEC-CMS-POINTS-001 — 총액 조회 성능 확보용. 적립 시 ledger insert와 함께 upsert.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPointSummary {

    private Long userId;
    private long totalPoints;
    private Instant updatedAt;
}
