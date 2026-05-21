package kr.co.ircp.cms.domain.system.stats.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.stats.dto.DashboardKpiResponse;
import kr.co.ircp.cms.domain.system.stats.service.DashboardServiceImpl;
import kr.co.ircp.cms.domain.system.stats.service.StatsService;
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

    // DashboardController는 StatsService도 주입받으므로 슬라이스에서 @MockitoBean으로 대체
    @MockitoBean
    private StatsService statsService;

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

        // 응답 DTO는 @JsonNaming(SnakeCaseStrategy)로 직렬화되므로 JSON 경로도 snake_case 사용
        mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today_visits").value(100))
                .andExpect(jsonPath("$.health_status").value("UP"));

        verify(dashboardService).getKpi(false);
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi (X-No-Cache: true) — 캐시 우회 후 즉시 재계산 200 OK")
    void kpi_noCacheTrue_invokesGetKpiFresh() throws Exception {
        when(dashboardService.getKpiFresh()).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi").header("X-No-Cache", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today_unique").value(80))
                .andExpect(jsonPath("$.error_rate_24h").value(0.5));

        verify(dashboardService).getKpiFresh();
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi (X-No-Cache: false) — 캐시 사용 200 OK")
    void kpi_noCacheFalse_usesCache() throws Exception {
        when(dashboardService.getKpi(eq(false))).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi").header("X-No-Cache", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avg_response_ms_24h").value(120));

        verify(dashboardService).getKpi(false);
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:DASHBOARD"})
    @DisplayName("GET /dashboard/kpi — 응답 모든 KPI 필드 검증 200 OK")
    void kpi_responseContainsAllKpiFields() throws Exception {
        when(dashboardService.getKpi(eq(false))).thenReturn(sample());

        mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today_visits").value(100))
                .andExpect(jsonPath("$.today_unique").value(80))
                .andExpect(jsonPath("$.today_page_views").value(250))
                .andExpect(jsonPath("$.today_signups").value(5))
                .andExpect(jsonPath("$.locked_accounts").value(2))
                .andExpect(jsonPath("$.audit_log_24h_count").value(50))
                .andExpect(jsonPath("$.audit_log_critical_24h_count").value(1));
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오
    // 메소드 레벨 @PreAuthorize("hasAuthority('SYSTEM:DASHBOARD')") 정책 검증
    // (AUTHZ-MATRIX-001 IT 레이어와 분리: 슬라이스 vs SpringBootTest)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-COV-001-1 — GET /api/v1/system/dashboard/kpi 인증 없이 접근 시 403 Forbidden (@WebMvcTest 한계)")
    void kpi_returns403_withoutAuthentication() throws Exception {
        // @WebMvcTest + SecurityAutoConfiguration 제외 → SecurityFilterChain 없음 → @PreAuthorize 거부 → 403
        // 401 검증은 SecurityConfig 통합 테스트에서 별도 (REQ-IRR-003).
        mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"WRONG_AUTHORITY"})
    @DisplayName("AC-COV-001-2 — GET /api/v1/system/dashboard/kpi 권한 부족 시 403 Forbidden")
    void kpi_returns403_withInsufficientAuthority() throws Exception {
        // given: WRONG_AUTHORITY는 SYSTEM:DASHBOARD 권한 미충족
        // when & then: @PreAuthorize 거부 → AccessDeniedHandler → 403
        mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                .andExpect(status().isForbidden());
    }
}
