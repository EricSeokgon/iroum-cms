package kr.co.ircp.cms.domain.media.dto;

import java.time.Instant;
import java.util.List;

/**
 * 미디어 컬렉션 상세 DTO (자산 목록 포함).
 * REQ-MEDIA-005-D-1, REQ-MEDIA-005-D-3
 */
public record MediaCollectionDetail(
        Long id,
        String name,
        String description,
        Long ownerId,
        boolean isPublic,
        int sortOrder,
        Instant createdAt,
        List<MediaAssetSummary> items
) {
}
