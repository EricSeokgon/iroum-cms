package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 게시글 목록 조회용 요약 DTO.
 * REQ-BOARD-002-Q-1: 게시글 페이징 목록 응답
 */
public record PostSummary(
        Long id,
        Long bbsMasterId,
        String bbsMasterCode,
        String title,
        Long authorId,
        String authorName,
        boolean isNotice,
        boolean isSecret,
        long viewCount,
        long commentCount,
        long attachmentCount,
        Instant createdAt
) {
}
