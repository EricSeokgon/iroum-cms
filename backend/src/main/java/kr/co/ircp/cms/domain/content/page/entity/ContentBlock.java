package kr.co.ircp.cms.domain.content.page.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 콘텐츠 블록 엔티티.
 * REQ-CONTENT-006-D: 5종 블록 타입 (RICH_TEXT/IMAGE/HTML/MARKDOWN/EMBED)
 *
 */
@Data
@Builder
public class ContentBlock {

    private Long id;
    private Long pageId;
    /** RICH_TEXT|IMAGE|HTML|MARKDOWN|EMBED */
    private String blockType;
    private int sortOrder;
    /** JSON 페이로드 (block_type별 스키마) */
    private String payload;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
}
