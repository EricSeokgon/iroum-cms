package kr.co.ircp.cms.domain.dashboard.dto;

import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;

import java.time.Instant;

/**
 * Export 응답 DTO.
 * REQ-VIZ-006-D-5
 */
public record ExportResponse(
        Long id,
        Long requestorId,
        String exportType,
        String scope,
        String filePath,
        Long sizeBytes,
        Integer rowCount,
        String status,
        Integer progressPct,
        String errorMessage,
        Instant requestedAt,
        Instant completedAt,
        Instant expiresAt,
        /** 서명된 다운로드 URL (REQ-VIZ-006-D-5). expires_at 이후 410 Gone */
        String signedDownloadUrl
) {
    public static ExportResponse from(ExportHistory e, String signedUrl) {
        return new ExportResponse(
                e.getId(), e.getRequestorId(), e.getExportType(), e.getScope(),
                e.getFilePath(), e.getSizeBytes(), e.getRowCount(),
                e.getStatus(), e.getProgressPct(), e.getErrorMessage(),
                e.getRequestedAt(), e.getCompletedAt(), e.getExpiresAt(),
                signedUrl);
    }
}
