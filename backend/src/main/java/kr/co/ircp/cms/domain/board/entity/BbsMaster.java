package kr.co.ircp.cms.domain.board.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시판 마스터 엔티티.
 * REQ-BOARD-001-D: 게시판 마스터 정의
 */
// @MX:ANCHOR: [AUTO] BbsMaster — 게시판 정책의 루트 엔티티. 모든 게시글·댓글·첨부 로직의 정책 참조 원점
// @MX:REASON: BbsMasterService, PostService, CommentService, AttachmentService 에서 fan_in >= 4로 참조
// @MX:NOTE: @NoArgsConstructor + @AllArgsConstructor 필수 — MyBatis DefaultObjectFactory가 리플렉션으로 no-args 생성자 호출.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BbsMaster {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String type;
    private boolean useComment;
    private boolean useAttachment;
    private int maxAttachmentCount;
    private int maxAttachmentSizeKb;
    private boolean allowAnonymous;
    private boolean allowSecret;
    private int pageSize;
    private String roleRequiredRead;
    private String roleRequiredWrite;
    private String status;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
