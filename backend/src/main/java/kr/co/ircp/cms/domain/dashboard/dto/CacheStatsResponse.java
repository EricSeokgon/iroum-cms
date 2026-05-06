package kr.co.ircp.cms.domain.dashboard.dto;

/**
 * 캐시 통계 응답.
 * REQ-VIZ-005 운영 가시성
 */
public record CacheStatsResponse(
        long activeEntries,
        long expiredEntries
) {
}
