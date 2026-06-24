package kr.co.ircp.cms.domain.point.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자 포인트 원장 엔티티.
 * SPEC-CMS-POINTS-001 REQ-PNT-002~004 — 포인트 지급/차감 이력
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPointLedger {

    private Long id;
    private Long userId;
    /** 지급(+) 또는 차감(-) 포인트 */
    private int delta;
    /** 지급 사유 코드: POST_CREATED, COMMENT_CREATED, LIKE_GIVEN */
    private String reason;
    /** 참조 엔티티 타입: POST, COMMENT */
    private String refType;
    private Long refId;
    private Instant createdAt;
}
