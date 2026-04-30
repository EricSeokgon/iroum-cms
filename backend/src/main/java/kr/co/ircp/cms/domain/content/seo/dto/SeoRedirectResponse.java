package kr.co.ircp.cms.domain.content.seo.dto;

import kr.co.ircp.cms.domain.content.seo.entity.SeoRedirect;

import java.time.Instant;

/**
 * SEO 리다이렉트 응답 DTO.
 */
public record SeoRedirectResponse(
        Long id,
        String fromPath,
        String toPath,
        short httpStatus,
        boolean active,
        String reason,
        Instant createdAt
) {
    public static SeoRedirectResponse from(SeoRedirect redirect) {
        return new SeoRedirectResponse(
                redirect.getId(),
                redirect.getFromPath(),
                redirect.getToPath(),
                redirect.getHttpStatus(),
                redirect.isActive(),
                redirect.getReason(),
                redirect.getCreatedAt()
        );
    }
}
