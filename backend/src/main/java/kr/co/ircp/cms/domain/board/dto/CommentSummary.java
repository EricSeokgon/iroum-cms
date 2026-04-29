package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * 댓글 목록 조회용 DTO.
 * REQ-BOARD-003-Q: 댓글 목록 응답 (계층형)
 */
public record CommentSummary(
        Long id,
        Long postId,
        Long parentCommentId,
        Long authorId,
        String authorName,
        String anonymousName,
        String content,
        String status,
        List<CommentSummary> replies,
        Instant createdAt,
        Instant updatedAt
) {
}
