package kr.co.ircp.cms.domain.content.page.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 페이지 엔티티.
 * REQ-CONTENT-005-D: 페이지 CRUD + 발행/예약/철회 + 변경 이력
 *
 * // @MX:ANCHOR: [AUTO] Page — 페이지 콘텐츠의 루트 엔티티
 * // @MX:REASON: PageService, ContentBlockService, PageHistoryService에서 fan_in >= 3으로 참조
 */
@Data
@Builder
public class Page {

    private Long id;
    private Long siteId;
    private Long templateId;
    private Long menuId;
    private String code;
    private String title;
    /** URL path 일부, 소문자/숫자/하이픈/슬래시만 허용 */
    private String slug;
    /** DRAFT|SCHEDULED|PUBLISHED|RETRACTED */
    private String status;
    private Instant publishedAt;
    private Instant scheduledAt;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private String ogImageUrl;
    private String canonicalUrl;
    /** 이력 버전 카운터 */
    private int currentVersion;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
