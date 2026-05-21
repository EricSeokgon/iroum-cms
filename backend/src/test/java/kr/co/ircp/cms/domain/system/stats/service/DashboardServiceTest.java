package kr.co.ircp.cms.domain.system.stats.service;

import kr.co.ircp.cms.domain.system.stats.dto.DashboardKpiResponse;
import kr.co.ircp.cms.domain.audit.repository.AuditLogMapper;
import kr.co.ircp.cms.domain.system.stats.entity.AccessStatDaily;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatDailyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * DashboardService GREEN 테스트.
 * REQ-SYSTEM-002-D: KPI 집계 + 오류율 계산
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService GREEN 테스트 (REQ-SYSTEM-002-D)")
class DashboardServiceTest {

    @Mock private AccessStatDailyMapper dailyMapper;
    @Mock private AuditLogMapper auditLogMapper;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(dailyMapper, auditLogMapper);
    }

    private AccessStatDaily daily(int visits, int unique, int pageViews, int errors, int avgMs) {
        return AccessStatDaily.builder()
                .statDate(LocalDate.now())
                .siteId(1L)
                .totalVisits(visits)
                .uniqueVisitors(unique)
                .uniqueSessions(unique)
                .pageViews(pageViews)
                .avgResponseMs(avgMs)
                .errorCount(errors)
                .build();
    }

    @Test
    @DisplayName("getKpi() — 오늘 통계가 없으면 0 반환")
    void getKpi_empty_stats_returns_zeros() {
        // given
        when(dailyMapper.findToday(1L)).thenReturn(Optional.empty());
        when(dailyMapper.findLast24hStats(1L)).thenReturn(null);

        // when
        DashboardKpiResponse kpi = dashboardService.getKpi(false);

        // then
        assertThat(kpi.todayVisits()).isEqualTo(0);
        assertThat(kpi.errorRate24h()).isEqualTo(0.0);
        // DashboardServiceImpl은 healthStatus로 "HEALTHY"를 반환함 (DTO 정의에 따라)
        assertThat(kpi.healthStatus()).isEqualTo("HEALTHY");
    }

    @Test
    @DisplayName("getKpi() — 오늘 방문 수 정상 반환")
    void getKpi_returns_today_visits() {
        // given
        when(dailyMapper.findToday(1L)).thenReturn(Optional.of(daily(500, 200, 1000, 5, 120)));
        when(dailyMapper.findLast24hStats(1L)).thenReturn(daily(500, 200, 1000, 5, 120));

        // when
        DashboardKpiResponse kpi = dashboardService.getKpi(false);

        // then
        assertThat(kpi.todayVisits()).isEqualTo(500);
        assertThat(kpi.todayUnique()).isEqualTo(200);
        assertThat(kpi.todayPageViews()).isEqualTo(1000);
    }

    @Test
    @DisplayName("getKpi() — 오류율이 비율(0~1)로 계산됨")
    void getKpi_error_rate_calculated_as_percentage() {
        // given — 1000건 중 50건 오류 = 0.05 (비율). 프론트엔드에서 *100 포맷팅
        when(dailyMapper.findToday(1L)).thenReturn(Optional.of(daily(1000, 400, 2000, 50, 80)));
        when(dailyMapper.findLast24hStats(1L)).thenReturn(daily(1000, 400, 2000, 50, 80));

        // when
        DashboardKpiResponse kpi = dashboardService.getKpi(false);

        // then — DashboardServiceImpl은 비율(0~1) 형태로 반환
        assertThat(kpi.errorRate24h()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("getKpi() — avgResponseMs 24h 반환")
    void getKpi_returns_avg_response_ms() {
        // given
        when(dailyMapper.findToday(1L)).thenReturn(Optional.of(daily(100, 50, 200, 0, 250)));
        when(dailyMapper.findLast24hStats(1L)).thenReturn(daily(100, 50, 200, 0, 250));

        // when
        DashboardKpiResponse kpi = dashboardService.getKpi(false);

        // then
        assertThat(kpi.avgResponseMs24h()).isEqualTo(250L);
    }
}
