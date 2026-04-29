package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * FAQ 엔티티.
 * REQ-BOARD-007-D: FAQ 카테고리·정렬·검색
 */
@Data
@Builder
public class Faq {

    private Long id;
    private String categoryCode;
    private String question;
    private String answerHtml;
    private String answerText;
    private int sortOrder;
    private long viewCount;
    private String status;
    private String metadata;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
