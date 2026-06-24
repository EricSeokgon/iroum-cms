package kr.co.ircp.cms.domain.content.block.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 공유 콘텐츠 블록 엔티티.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 REQ-CB-001 — 고유 slug 를 가진 재사용 명명 블록.
 * block_type / status 는 DB CHECK 제약과 일치하는 문자열로 보관한다(enum 미사용).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedContentBlock {

    private Long id;
    private String name;
    private String slug;
    /** "RICH_TEXT" | "HTML" | "MARKDOWN" | "EMBED" */
    private String blockType;
    private String contentHtml;
    private String contentRaw;
    private String description;
    /** "ACTIVE" | "INACTIVE" */
    private String status;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
