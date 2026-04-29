package kr.co.ircp.cms.domain.board.dto;

import kr.co.ircp.cms.domain.board.entity.BbsType;

import java.time.Instant;

/**
 * 게시판 마스터 상세 조회용 DTO.
 * REQ-BOARD-001-Q-2: 게시판 단건 상세 응답
 */
public record BbsMasterDetail(
        Long id,
        String code,
        String name,
        String description,
        BbsType type,
        boolean useComment,
        boolean useAttachment,
        int maxAttachmentCount,
        long maxAttachmentSizeKb,
        boolean allowAnonymous,
        boolean allowSecret,
        int pageSize,
        String roleRequiredRead,
        String roleRequiredWrite,
        String status,
        String metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
