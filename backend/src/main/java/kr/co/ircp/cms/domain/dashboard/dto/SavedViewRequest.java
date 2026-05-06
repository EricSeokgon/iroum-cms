package kr.co.ircp.cms.domain.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 저장된 뷰 등록/수정 요청 DTO.
 * REQ-VIZ-004-D-3
 */
public record SavedViewRequest(
        Long dashboardId,
        @NotBlank @Size(max = 128) String name,
        String description,
        @NotNull String filterState,
        Boolean isDefault,
        Boolean isShared,
        List<String> sharedWith
) {
}
