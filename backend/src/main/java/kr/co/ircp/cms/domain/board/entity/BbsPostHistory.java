package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 게시글 변경 이력 엔티티.
 * REQ-BOARD-002-D-4: 수정 직전 본문 보존
 */
@Data
@Builder
public class BbsPostHistory {

    private Long id;
    private Long postId;
    private int version;
    private String title;
    private String contentHtml;
    private Long editedBy;
    private String editReason;
    private Instant editedAt;
}
