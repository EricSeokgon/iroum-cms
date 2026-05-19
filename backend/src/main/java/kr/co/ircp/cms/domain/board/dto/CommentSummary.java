package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * 댓글 목록 조회용 DTO.
 * REQ-BOARD-003-Q: 댓글 목록 응답 (계층형)
 *
 * // @MX:NOTE: [AUTO] 프론트엔드 TypeScript 인터페이스(CommentSummary)와 필드명 일치 필요.
 * //           authorUsername: 로그인 사용자의 username (익명 댓글은 null).
 * //           children: 1단계 대댓글 목록 (replies → children 으로 리네임).
 */
public record CommentSummary(
        Long id,
        Long postId,
        Long parentCommentId,
        Long authorId,
        String authorUsername,
        String anonymousName,
        String content,
        String status,
        List<CommentSummary> children,
        Instant createdAt,
        Instant updatedAt
) {
}
