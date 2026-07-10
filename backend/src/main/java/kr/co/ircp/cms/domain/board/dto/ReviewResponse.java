package kr.co.ircp.cms.domain.board.dto;

import kr.co.ircp.cms.domain.board.entity.BbsPostReview;

import java.time.Instant;

/**
 * 공개 리뷰 응답 DTO (VISIBLE 목록·작성 응답용).
 * SPEC-CMS-REVIEW-001 REQ-REV-005 — IP·status 등 관리 전용 필드는 노출하지 않는다.
 */
public record ReviewResponse(
        Long id,
        Long postId,
        Long authorId,
        int rating,
        String content,
        Instant createdAt
) {

    /** 엔티티 → 공개 응답 변환. */
    public static ReviewResponse from(BbsPostReview r) {
        return new ReviewResponse(
                r.getId(), r.getPostId(), r.getAuthorId(),
                r.getRating(), r.getContent(), r.getCreatedAt()
        );
    }
}
