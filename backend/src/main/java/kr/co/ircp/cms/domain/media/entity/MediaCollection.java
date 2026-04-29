package kr.co.ircp.cms.domain.media.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 미디어 컬렉션(앨범·즐겨찾기) 엔티티.
 * REQ-MEDIA-005-D: 사용자별 컬렉션 CRUD
 */
@Data
@Builder
public class MediaCollection {

    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private boolean isPublic;
    private int sortOrder;
    private Instant createdAt;
}
