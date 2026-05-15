package kr.co.ircp.cms.domain.content.banner.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 배너 엔티티.
 * REQ-CONTENT-009-D: 배너 CRUD + 클릭 카운트 + 그룹별 조회
 *
 * // @MX:ANCHOR: [AUTO] Banner — 배너 콘텐츠 루트 엔티티
 * // @MX:REASON: BannerService, BannerController, SitemapService에서 fan_in >= 3으로 참조
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Banner {

    private Long id;
    private Long siteId;
    /** 배너 그룹 코드 (예: HOME_HERO, SIDE_TOP) */
    private String bannerGroupCode;
    private String title;
    private String imageUrl;
    private String linkUrl;
    /** _self|_blank */
    private String linkTarget;
    /** KWCAG 2.2 AA 1.1.1 대체텍스트 — NOT NULL */
    private String altText;
    private Instant displayFrom;
    private Instant displayUntil;
    private int sortOrder;
    private long clickCount;
    /** ACTIVE|INACTIVE */
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
