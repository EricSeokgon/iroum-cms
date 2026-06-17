package kr.co.ircp.cms.domain.point.dto;

import kr.co.ircp.cms.domain.point.entity.UserPointLedger;

import java.time.Instant;

/**
 * 포인트 적립 내역 응답.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-006 — 관리자/사용자 내역 조회 응답 항목.
 */
public record PointLedgerResponse(
        Long id,
        Long userId,
        String eventType,
        Long referenceId,
        int points,
        Instant createdAt
) {
    public static PointLedgerResponse from(UserPointLedger l) {
        return new PointLedgerResponse(
                l.getId(), l.getUserId(), l.getEventType(),
                l.getReferenceId(), l.getPoints(), l.getCreatedAt());
    }
}
