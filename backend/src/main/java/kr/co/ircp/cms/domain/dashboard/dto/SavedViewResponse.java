package kr.co.ircp.cms.domain.dashboard.dto;

import kr.co.ircp.cms.domain.dashboard.entity.SavedView;

import java.time.Instant;
import java.util.List;

/**
 * 저장된 뷰 응답 DTO.
 * REQ-VIZ-004
 */
public record SavedViewResponse(
        Long id,
        Long ownerId,
        Long dashboardId,
        String name,
        String description,
        String filterState,
        boolean isDefault,
        boolean isShared,
        List<String> sharedWith,
        Instant createdAt,
        Instant lastUsedAt
) {
    public static SavedViewResponse from(SavedView v) {
        return new SavedViewResponse(
                v.getId(), v.getOwnerId(), v.getDashboardId(), v.getName(),
                v.getDescription(), v.getFilterState(), v.isDefault(), v.isShared(),
                v.getSharedWith(), v.getCreatedAt(), v.getLastUsedAt());
    }
}
