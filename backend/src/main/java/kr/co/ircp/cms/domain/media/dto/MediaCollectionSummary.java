package kr.co.ircp.cms.domain.media.dto;

import kr.co.ircp.cms.domain.media.entity.MediaCollection;

import java.time.Instant;

/**
 * 미디어 컬렉션 요약 DTO.
 * REQ-MEDIA-005-D-1
 */
public record MediaCollectionSummary(
        Long id,
        String name,
        String description,
        Long ownerId,
        boolean isPublic,
        int sortOrder,
        Instant createdAt
) {
    public static MediaCollectionSummary from(MediaCollection c) {
        return new MediaCollectionSummary(
                c.getId(), c.getName(), c.getDescription(), c.getOwnerId(),
                c.isPublic(), c.getSortOrder(), c.getCreatedAt()
        );
    }
}
