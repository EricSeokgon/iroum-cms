package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 댓글 엔티티 (1단계 대댓글 포함).
 * REQ-BOARD-003-D: 댓글 CRUD
 */
@Data
@Builder
public class BbsComment {

    private Long id;
    private Long postId;
    private Long parentCommentId;
    private Long authorId;
    private String anonymousName;
    private String anonymousPwdHash;
    private String content;
    private String ipAddress;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
