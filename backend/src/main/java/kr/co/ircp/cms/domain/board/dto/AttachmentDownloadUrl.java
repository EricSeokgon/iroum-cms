package kr.co.ircp.cms.domain.board.dto;

import java.time.Instant;

/**
 * HMAC-SHA256 서명된 다운로드 URL DTO.
 * REQ-BOARD-005: 첨부파일 보안 다운로드 (서명 URL, TTL 15분)
 */
public record AttachmentDownloadUrl(
        Long attachmentId,
        String fileName,
        String downloadUrl,
        Instant expiresAt
) {
}
