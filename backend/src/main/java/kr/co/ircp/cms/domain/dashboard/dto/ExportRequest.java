package kr.co.ircp.cms.domain.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Export 요청 DTO.
 * REQ-VIZ-006-D-1~5
 */
public record ExportRequest(
        @NotBlank String exportType,
        @NotNull String scope,
        Boolean async
) {
    public boolean isAsyncRequested() {
        return Boolean.TRUE.equals(async);
    }
}
