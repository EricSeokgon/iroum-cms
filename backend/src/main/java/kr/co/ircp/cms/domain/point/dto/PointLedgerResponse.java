package kr.co.ircp.cms.domain.point.dto;

import kr.co.ircp.cms.domain.point.entity.UserPointLedger;

import java.time.Instant;

/**
 * 포인트 이력 응답 DTO.
 * SPEC-CMS-POINTS-001 REQ-PNT-007
 */
public record PointLedgerResponse(
        Long id,
        Long userId,
        int delta,
        String reason,
        String refType,
        Long refId,
        Instant createdAt
) {
    public static PointLedgerResponse from(UserPointLedger e) {
        return new PointLedgerResponse(
                e.getId(), e.getUserId(), e.getDelta(),
                e.getReason(), e.getRefType(), e.getRefId(),
                e.getCreatedAt()
        );
    }
}
