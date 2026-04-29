package kr.co.ircp.cms.domain.media.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 컬렉션-자산 매핑 엔티티.
 * PK: (collection_id, asset_id)
 * REQ-MEDIA-005-D-3: sort_order 보존
 */
@Data
@Builder
public class MediaCollectionItem {

    private Long collectionId;
    private Long assetId;
    private int sortOrder;
    private Instant addedAt;
}
