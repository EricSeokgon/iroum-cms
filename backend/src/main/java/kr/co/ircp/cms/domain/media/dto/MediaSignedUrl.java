package kr.co.ircp.cms.domain.media.dto;

import java.time.Instant;

/**
 * HMAC-SHA256 서명 다운로드 URL 응답 DTO.
 * REQ-MEDIA-004-D-5, TTL 15분 기본값
 */
public record MediaSignedUrl(
        String signedUrl,
        Instant expiresAt
) {
}
