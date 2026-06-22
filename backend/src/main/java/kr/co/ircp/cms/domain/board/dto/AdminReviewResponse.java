package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 관리자 리뷰 목록 응답 DTO.
 * SPEC-CMS-REVIEW-001 REQ-REV-004 — 모든 상태(VISIBLE/HIDDEN) 노출 + 관리 전용 필드(status, ipAddress, authorName).
 *
 * <p>MyBatis constructor 매핑 사용(컬럼 순서 = 생성자 인자 순서). record 는 setter 가 없으므로 생성자 매핑 필수.
 */
public record AdminReviewResponse(
        Long id,
        Long postId,
        String postTitle,
        Long authorId,
        String authorName,
        int rating,
        String content,
        String status,
        String ipAddress,
        Instant createdAt
) {
}
