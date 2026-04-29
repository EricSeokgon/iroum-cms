package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 게시글 엔티티.
 * REQ-BOARD-002-D: 게시글 CRUD + 페이징·검색
 */
@Data
@Builder
public class BbsPost {

    private Long id;
    private Long bbsId;
    private String title;
    private String contentHtml;
    private String contentText;
    private String categoryCode;
    private Long authorId;
    private String authorName;
    private boolean isNotice;
    private Instant noticeFrom;
    private Instant noticeUntil;
    private boolean isSecret;
    private long viewCount;
    private long likeCount;
    private int commentCount;
    private int attachmentCount;
    private String status;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
