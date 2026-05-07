package kr.co.ircp.cms.domain.system.stats.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.stats.dto.DashboardKpiResponse;
import kr.co.ircp.cms.domain.system.stats.service.DashboardServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DashboardController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-002-D: 운영 대시보드 KPI 조회 (60초 캐시 + X-No-Cache 우회) HTTP 계층 검증.
 */
@WebMvcTest(DashboardController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("DashboardController GREEN 테스트 (REQ-SYSTEM-002-D)")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardServiceImpl dashboardService;

    private static DashboardKpiResponse sample() {
        return DashboardKpiResponse.builder()
                .todayVisits(100)
                .todayUnique(80)
                .todayPageViews(250)
                .todaySignups(5)
                .errorRate24h(0.5)
                .avgResponseMs24h(120L)
                .lockedAccounts(2L)
                .auditLog24hCount(50L)
                .auditLogCritical24hCount(1L)
                .healthStatus("UP")
                .build();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi — 캐시 사용 200 OK")
    void kpi_default_returnsOkFromCache() throws Exception {
        when(dashboardService.getKpi(eq(false))).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayVisits").value(100))
                .andExpect(jsonPath("$.healthStatus").value("UP"));

        verify(dashboardService).getKpi(false);
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi (X-No-Cache: true) — 캐시 우회 후 즉시 재계산 200 OK")
    void kpi_noCacheTrue_invokesGetKpiFresh() throws Exception {
        when(dashboardService.getKpiFresh()).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi").header("X-No-Cache", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayUnique").value(80))
                .andExpect(jsonPath("$.errorRate24h").value(0.5));

        verify(dashboardService).getKpiFresh();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi (X-No-Cache: false) — 캐시 사용 200 OK")
    void kpi_noCacheFalse_usesCache() throws Exception {
        when(dashboardService.getKpi(eq(false))).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi").header("X-No-Cache", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgResponseMs24h").value(120));

        verify(dashboardService).getKpi(false);
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi — 응답 모든 KPI 필드 검증 200 OK")
    void kpi_responseContainsAllKpiFields() throws Exception {
        when(dashboardService.getKpi(eq(false))).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayVisits").value(100))
                .andExpect(jsonPath("$.todayUnique").value(80))
                .andExpect(jsonPath("$.todayPageViews").value(250))
                .andExpect(jsonPath("$.todaySignups").value(5))
                .andExpect(jsonPath("$.lockedAccounts").value(2))
                .andExpect(jsonPath("$.auditLog24hCount").value(50))
                .andExpect(jsonPath("$.auditLogCritical24hCount").value(1));
    }
}
