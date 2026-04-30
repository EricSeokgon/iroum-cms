package kr.co.ircp.cms.domain.content.banner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 배너 생성/수정 요청 DTO.
 * REQ-CONTENT-009-D-1: 배너 등록 (display_from < display_until + alt_text NOT NULL)
 */
public record BannerRequest(
        @NotNull Long siteId,
        @NotBlank String bannerGroupCode,
        @NotBlank String title,
        @NotBlank String imageUrl,
        String linkUrl,
        String linkTarget,
        @NotBlank String altText,
        @NotNull Instant displayFrom,
        @NotNull Instant displayUntil,
        Integer sortOrder
) {}
