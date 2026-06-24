package kr.co.ircp.cms.domain.point.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시글 좋아요 엔티티.
 * SPEC-CMS-POINTS-001 REQ-PNT-004 — UNIQUE(user_id, post_id) 보장
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsPostLike {

    private Long id;
    private Long userId;
    private Long postId;
    private Instant createdAt;
}
