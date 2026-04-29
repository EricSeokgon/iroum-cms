package kr.co.ircp.cms.domain.content.site.dto;

import kr.co.ircp.cms.domain.content.site.entity.Site;

import java.time.Instant;

/**
 * 사이트 응답 DTO.
 * REQ-CONTENT-003-D-2: 사이트 정보 조회 응답
 */
public record SiteResponse(
        Long id,
        String code,
        String name,
        String domain,
        String defaultLanguage,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SiteResponse from(Site site) {
        return new SiteResponse(
                site.getId(),
                site.getCode(),
                site.getName(),
                site.getDomain(),
                site.getDefaultLanguage(),
                site.getStatus(),
                site.getCreatedAt(),
                site.getUpdatedAt()
        );
    }
}
