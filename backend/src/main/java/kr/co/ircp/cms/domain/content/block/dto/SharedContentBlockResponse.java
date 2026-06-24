package kr.co.ircp.cms.domain.content.block.dto;

import kr.co.ircp.cms.domain.content.block.entity.SharedContentBlock;

import java.time.Instant;

/**
 * 공유 콘텐츠 블록 응답 DTO.
 *
 * <p>SPEC-CMS-CONTENT-BLOCK-001 — 목록/단건/생성/수정 공통 응답 형식.
 *
 * <p>Shared prefix 사유: {@link SharedContentBlockRequest} 참조 (MyBatis alias 충돌 회피).
 */
public record SharedContentBlockResponse(
        Long id,
        String name,
        String slug,
        String blockType,
        String contentHtml,
        String contentRaw,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static SharedContentBlockResponse from(SharedContentBlock block) {
        return new SharedContentBlockResponse(
                block.getId(), block.getName(), block.getSlug(),
                block.getBlockType(), block.getContentHtml(), block.getContentRaw(),
                block.getDescription(), block.getStatus(),
                block.getCreatedAt(), block.getUpdatedAt()
        );
    }
}
