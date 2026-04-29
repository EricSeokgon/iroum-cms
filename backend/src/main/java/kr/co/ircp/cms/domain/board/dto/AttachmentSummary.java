package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * 첨부파일 목록 조회용 DTO.
 * REQ-BOARD-004-Q: 첨부파일 정보 응답
 */
public record AttachmentSummary(
        Long id,
        Long postId,
        String fileName,
        String mimeType,
        long sizeBytes,
        String scanStatus,
        long downloadCount,
        Instant uploadedAt
) {
}
