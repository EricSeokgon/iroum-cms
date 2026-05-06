package kr.co.ircp.cms.domain.dashboard.dto;

import java.util.List;

/**
 * 캐시 무효화 요청 DTO.
 * REQ-VIZ-005-D-5
 */
public record CacheInvalidateRequest(
        List<Long> widgetIds,
        List<Long> kpiIds,
        Boolean all
) {
    public boolean invalidateAll() {
        return Boolean.TRUE.equals(all);
    }
}
