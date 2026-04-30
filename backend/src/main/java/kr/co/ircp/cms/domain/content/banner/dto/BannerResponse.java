package kr.co.ircp.cms.domain.content.banner.dto;

import kr.co.ircp.cms.domain.content.banner.entity.Banner;

import java.time.Instant;

/**
 * 배너 응답 DTO.
 * REQ-CONTENT-009-D: 배너 조회 응답
 */
public record BannerResponse(
        Long id,
        Long siteId,
        String bannerGroupCode,
        String title,
        String imageUrl,
        String linkUrl,
        String linkTarget,
        String altText,
        Instant displayFrom,
        Instant displayUntil,
        int sortOrder,
        long clickCount,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BannerResponse from(Banner banner) {
        return new BannerResponse(
                banner.getId(),
                banner.getSiteId(),
                banner.getBannerGroupCode(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getLinkUrl(),
                banner.getLinkTarget(),
                banner.getAltText(),
                banner.getDisplayFrom(),
                banner.getDisplayUntil(),
                banner.getSortOrder(),
                banner.getClickCount(),
                banner.getStatus(),
                banner.getCreatedAt(),
                banner.getUpdatedAt()
        );
    }
}
