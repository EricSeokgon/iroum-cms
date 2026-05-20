package kr.co.ircp.cms.domain.system.stats.service;

import kr.co.ircp.cms.domain.audit.repository.AuditLogMapper;
import kr.co.ircp.cms.domain.system.stats.dto.DashboardKpiResponse;
import kr.co.ircp.cms.domain.system.stats.entity.AccessStatDaily;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatDailyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 대시보드 서비스 구현체.
 *
 * <p>REQ-SYSTEM-002-D — KPI 60초 TTL Caffeine 캐시.
 * X-No-Cache: true 요청 시 캐시를 무효화하고 즉시 재계산한다.
 */
// @MX:WARN: [AUTO] @Cacheable + @CacheEvict 조합 — 같은 메서드에 적용 불가로 분리
// @MX:REASON: Spring Cache proxy는 같은 bean 내부 호출 시 캐시 적용 안 됨. 컨트롤러에서 분기
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AccessStatDailyMapper dailyMapper;
    private final AuditLogMapper auditLogMapper;

    // @MX:ANCHOR: [AUTO] getKpi — 대시보드 KPI 조회 진입점 (컨트롤러에서 fan_in >= 3)
    // @MX:REASON: DashboardController, StatsController, HealthController에서 참조
    @Override
    @Cacheable(value = "dashboard", key = "'kpi:1'")
    @Transactional(readOnly = true)
    public DashboardKpiResponse getKpi(boolean noCache) {
        return computeKpi();
    }

    @CacheEvict(value = "dashboard", allEntries = true)
    @Transactional(readOnly = true)
    public DashboardKpiResponse getKpiFresh() {
        return computeKpi();
    }

    private DashboardKpiResponse computeKpi() {
        AccessStatDaily today = dailyMapper.findToday(1L).orElse(emptyDaily());
        AccessStatDaily last24h = dailyMapper.findLast24hStats(1L);
        if (last24h == null) last24h = emptyDaily();

        int totalVisits = today.getTotalVisits() != null ? today.getTotalVisits() : 0;
        int errorCount = last24h.getErrorCount() != null ? last24h.getErrorCount() : 0;
        int totalForRate = last24h.getTotalVisits() != null && last24h.getTotalVisits() > 0
                ? last24h.getTotalVisits() : 1;
        // 비율(0~1)로 반환 — 프론트엔드에서 * 100 포맷팅
        double errorRate = (double) errorCount / totalForRate;

        return DashboardKpiResponse.builder()
                .todayVisits(totalVisits)
                .todayUnique(today.getUniqueVisitors() != null ? today.getUniqueVisitors() : 0)
                .todayPageViews(today.getPageViews() != null ? today.getPageViews() : 0)
                .todaySignups(0)
                .errorRate24h(Math.round(errorRate * 10000.0) / 10000.0)
                .avgResponseMs24h(last24h.getAvgResponseMs() != null
                        ? last24h.getAvgResponseMs().longValue() : 0L)
                .lockedAccounts(0L)
                .auditLog24hCount(auditLogMapper.countLast24h())
                .auditLogCritical24hCount(auditLogMapper.countCriticalLast24h())
                .healthStatus("HEALTHY")
                .build();
    }

    private AccessStatDaily emptyDaily() {
        return AccessStatDaily.builder()
                .statDate(LocalDate.now())
                .siteId(1L)
                .totalVisits(0)
                .uniqueVisitors(0)
                .uniqueSessions(0)
                .pageViews(0)
                .avgResponseMs(0)
                .errorCount(0)
                .build();
    }
}
