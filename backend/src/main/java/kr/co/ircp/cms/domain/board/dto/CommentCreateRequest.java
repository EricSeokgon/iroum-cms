package kr.co.ircp.cms.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 작성 요청 DTO.
 * REQ-BOARD-003-C: 댓글 생성
 */
public record CommentCreateRequest(
        Long parentCommentId,
        @NotBlank @Size(max = 4000) String content,
        String anonymousName,
        String anonymousPwd,
        String ipAddress
) {
}
