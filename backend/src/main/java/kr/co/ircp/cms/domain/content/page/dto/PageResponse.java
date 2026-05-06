package kr.co.ircp.cms.domain.content.page.dto;

import kr.co.ircp.cms.domain.content.page.entity.Page;
import org.apache.ibatis.type.Alias;

import java.time.Instant;

/**
 * 페이지 응답 DTO.
 * REQ-CONTENT-005-D: 페이지 조회 응답
 */
@Alias("ContentPageResponse")
public record PageResponse(
        Long id,
        Long siteId,
        Long templateId,
        Long menuId,
        String code,
        String title,
        String slug,
        String status,
        Instant publishedAt,
        Instant scheduledAt,
        String seoTitle,
        String seoDescription,
        String ogImageUrl,
        String canonicalUrl,
        int currentVersion,
        Instant createdAt,
        Instant updatedAt
) {
    public static PageResponse from(Page page) {
        return new PageResponse(
                page.getId(),
                page.getSiteId(),
                page.getTemplateId(),
                page.getMenuId(),
                page.getCode(),
                page.getTitle(),
                page.getSlug(),
                page.getStatus(),
                page.getPublishedAt(),
                page.getScheduledAt(),
                page.getSeoTitle(),
                page.getSeoDescription(),
                page.getOgImageUrl(),
                page.getCanonicalUrl(),
                page.getCurrentVersion(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }
}
