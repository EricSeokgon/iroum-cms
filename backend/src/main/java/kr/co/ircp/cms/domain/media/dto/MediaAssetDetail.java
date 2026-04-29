package kr.co.ircp.cms.domain.media.dto;

import kr.co.ircp.cms.domain.media.entity.MediaAsset;
import kr.co.ircp.cms.domain.media.entity.MediaStatus;
import kr.co.ircp.cms.domain.media.entity.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 미디어 자산 상세 DTO.
 * REQ-MEDIA-003-D-1, REQ-MEDIA-003-D-5
 */
public record MediaAssetDetail(
        Long id,
        UUID uuid,
        MediaType type,
        String originalFilename,
        String publicUrl,
        String mimeType,
        long sizeBytes,
        String checksumSha256,
        Integer width,
        Integer height,
        Double durationSec,
        boolean exifStripped,
        String webpPath,
        String thumbnailPathsJson,
        String altText,
        String description,
        List<String> tags,
        String copyrightHolder,
        String licenseType,
        String usageRestriction,
        Long uploadedBy,
        MediaStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static MediaAssetDetail from(MediaAsset a) {
        return new MediaAssetDetail(
                a.getId(), a.getUuid(), a.getType(), a.getOriginalFilename(),
                a.getPublicUrl(), a.getMimeType(), a.getSizeBytes(), a.getChecksumSha256(),
                a.getWidth(), a.getHeight(), a.getDurationSec(), a.isExifStripped(),
                a.getWebpPath(), a.getThumbnailPathsJson(), a.getAltText(), a.getDescription(),
                a.getTags(),
                a.getCopyrightHolder(),
                a.getLicenseType() != null ? a.getLicenseType().name() : null,
                a.getUsageRestriction(), a.getUploadedBy(), a.getStatus(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
