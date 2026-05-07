package kr.co.ircp.cms.domain.board.dto;

import java.util.UUID;

/**
 * 발간자료 ZIP 다운로드 응답 DTO.
 * REQ-BOARD-012-D-4: 동기(SYNC, ≤50MB) 또는 비동기(ASYNC, >50MB) 모드 안내
 */
public record ZipDownloadResponse(
        UUID downloadId,
        // SYNC | ASYNC
        String mode,
        String message,
        Long sizeBytes
) {
}
