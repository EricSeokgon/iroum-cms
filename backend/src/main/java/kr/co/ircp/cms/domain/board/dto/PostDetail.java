package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;
import java.util.List;

/**
 * 게시글 상세 조회용 DTO.
 * REQ-BOARD-002-Q-2: 게시글 단건 상세 응답 (조회수 증가 포함)
 */
public record PostDetail(
        Long id,
        Long bbsMasterId,
        String bbsMasterCode,
        boolean useComment,
        String title,
        String contentHtml,
        Long authorId,
        String authorName,
        boolean isNotice,
        Instant noticeFrom,
        Instant noticeUntil,
        boolean isSecret,
        long viewCount,
        long commentCount,
        String status,
        String metadata,
        List<AttachmentSummary> attachments,
        Instant createdAt,
        Instant updatedAt
) {
}
