package kr.co.ircp.cms.domain.point.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 포인트 적립 원장 엔티티 (append-only).
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-002/003/004 — 게시글/댓글/좋아요 적립 이벤트를
 * 1행씩 기록하는 거래 로그. 차감/수정 없음(earn-only).
 */
// @MX:NOTE: @NoArgsConstructor + @AllArgsConstructor 필수 — MyBatis DefaultObjectFactory 리플렉션 호환.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPointLedger {

    private Long id;
    private Long userId;
    /** POST_CREATED | COMMENT_CREATED | LIKE_GIVEN */
    private String eventType;
    /** 원인 행위 식별자(post_id / comment_id). */
    private Long referenceId;
    private int points;
    private Instant createdAt;
}
