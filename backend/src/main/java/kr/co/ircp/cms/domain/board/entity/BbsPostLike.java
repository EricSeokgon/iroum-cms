package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시글 좋아요 엔티티.
 *
 * <p>SPEC-CMS-POINTS-001 REQ-PNT-004 — 1인 1게시글 좋아요 추적.
 * DB 레벨 UNIQUE(user_id, post_id) 제약으로 중복 좋아요(=중복 적립)를 차단한다.
 */
// @MX:NOTE: @NoArgsConstructor + @AllArgsConstructor 필수 — MyBatis DefaultObjectFactory 리플렉션 호환.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsPostLike {

    private Long id;
    private Long postId;
    private Long userId;
    private Instant createdAt;
}
