package kr.co.ircp.cms.domain.board.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 발간자료 메타 엔티티 (bbs_post와 1:1).
 * REQ-BOARD-012-D: 발간자료 메타데이터 + bbs_post 조인 결과
 */
@Data
@Builder
public class PublicationMeta {

    private Long postId;
    private short publicationYear;
    private Short publicationMonth;
    private String documentType;
    private Long publicationCategoryId;
    private int fileCount;
    private String isbn;
    private String publisher;
    /** JSONB → String (애플리케이션 단에서 파싱). */
    private String metadata;

    // ─── bbs_post 조인 필드 ─────────────────────────────────────────────────
    private String title;
    private String contentHtml;
    private String contentText;
    private long viewCount;
    private String status;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // ─── publication_category 조인 필드 ─────────────────────────────────────
    private String categoryName;
}
