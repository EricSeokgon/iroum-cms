package kr.co.ircp.cms.domain.system.stats.dto;

import lombok.Builder;

/**
 * 운영 대시보드 KPI 응답 DTO.
 *
 * <p>REQ-SYSTEM-002-D, REQ-SYSTEM-003-D — 60초 TTL Caffeine 캐시.
 */
@Builder
public record DashboardKpiResponse(
        Integer todayVisits,
        Integer todayUnique,
        Integer todayPageViews,
        Integer todaySignups,
        Double errorRate24h,
        Long avgResponseMs24h,
        Long lockedAccounts,
        Long auditLog24hCount,
        Long auditLogCritical24hCount,
        String healthStatus
) {}
