package kr.co.ircp.cms.domain.content.page.dto;

import kr.co.ircp.cms.domain.content.page.entity.ContentBlock;

import java.time.Instant;

/**
 * 콘텐츠 블록 응답 DTO.
 * REQ-CONTENT-006-D: 블록 조회 응답
 */
public record ContentBlockResponse(
        Long id,
        Long pageId,
        String blockType,
        int sortOrder,
        String payload,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ContentBlockResponse from(ContentBlock block) {
        return new ContentBlockResponse(
                block.getId(),
                block.getPageId(),
                block.getBlockType(),
                block.getSortOrder(),
                block.getPayload(),
                block.getVersion(),
                block.getCreatedAt(),
                block.getUpdatedAt()
        );
    }
}
