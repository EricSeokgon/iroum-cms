package kr.co.ircp.cms.domain.media.dto;

import kr.co.ircp.cms.domain.media.entity.MediaAsset;
import kr.co.ircp.cms.domain.media.entity.MediaStatus;
import kr.co.ircp.cms.domain.media.entity.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 미디어 자산 요약 DTO (목록 조회용).
 * REQ-MEDIA-003-D-1: 페이지네이션 목록
 */
public record MediaAssetSummary(
        Long id,
        UUID uuid,
        MediaType type,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        MediaStatus status,
        String altText,
        List<String> tags,
        String licenseType,
        Long uploadedBy,
        Instant createdAt
) {
    public static MediaAssetSummary from(MediaAsset a) {
        return new MediaAssetSummary(
                a.getId(), a.getUuid(), a.getType(), a.getOriginalFilename(),
                a.getMimeType(), a.getSizeBytes(), a.getStatus(),
                a.getAltText(), a.getTags(),
                a.getLicenseType() != null ? a.getLicenseType().name() : null,
                a.getUploadedBy(), a.getCreatedAt()
        );
    }
}
