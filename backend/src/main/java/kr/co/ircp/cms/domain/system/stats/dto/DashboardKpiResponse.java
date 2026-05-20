package kr.co.ircp.cms.domain.system.stats.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;

/**
 * 운영 대시보드 KPI 응답 DTO.
 *
 * <p>REQ-SYSTEM-002-D, REQ-SYSTEM-003-D — 60초 TTL Caffeine 캐시.
 * // @MX:NOTE: [AUTO] 숫자 포함 필드는 SnakeCaseStrategy가 '_' 미삽입 → @JsonProperty 명시
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DashboardKpiResponse(
        Integer todayVisits,
        Integer todayUnique,
        Integer todayPageViews,
        Integer todaySignups,
        @JsonProperty("error_rate_24h") Double errorRate24h,
        @JsonProperty("avg_response_ms_24h") Long avgResponseMs24h,
        Long lockedAccounts,
        @JsonProperty("audit_log_24h_count") Long auditLog24hCount,
        @JsonProperty("audit_log_critical_24h_count") Long auditLogCritical24hCount,
        String healthStatus
) {}
