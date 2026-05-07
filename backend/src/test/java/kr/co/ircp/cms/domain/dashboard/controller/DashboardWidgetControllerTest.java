package kr.co.ircp.cms.domain.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetDataResponse;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetRequest;
import kr.co.ircp.cms.domain.dashboard.dto.WidgetResponse;
import kr.co.ircp.cms.domain.dashboard.service.DashboardWidgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DashboardWidgetController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-008 REQ-DASHBOARD-001 (REQ-VIZ-001, REQ-VIZ-005):
 * 위젯 CRUD + 미리보기 + 데이터/시계열 조회 HTTP 계층 검증.
 */
@WebMvcTest(DashboardWidgetController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("DashboardWidgetController GREEN 테스트 (REQ-DASHBOARD-001)")
class DashboardWidgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DashboardWidgetService service;

    private static WidgetResponse sampleWidget(Long id, String code, String type) {
        return new WidgetResponse(
                id, code, "위젯 이름", "설명",
                type, "KPI_CACHE", "{}", "{}",
                List.of("period", "feature"), List.of("DEPT_ADMIN"),
                "ACTIVE",
                Instant.now(), Instant.now()
        );
    }

    private static WidgetDataResponse sampleData(Long id, String code, String type) {
        return new WidgetDataResponse(
                new WidgetDataResponse.WidgetSummary(id, code, type),
                List.of("period", "feature"),
                Map.of("period", "7d"),
                new WidgetDataResponse.Dataset(
                        List.of("Mon", "Tue", "Wed"),
                        List.of(new WidgetDataResponse.Series("PV", List.of(10, 20, 30)))
                ),
                Instant.now(),
                true
        );
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:WIDGET:READ"})
    @DisplayName("GET /dashboard/widgets — 위젯 목록 200 OK")
    void list_returnsOkWithWidgets() throws Exception {
        when(service.list(eq("BAR_CHART"), eq("ACTIVE"), anyInt(), anyInt())).thenReturn(List.of(
                sampleWidget(1L, "PV_BY_FEATURE", "BAR_CHART"),
                sampleWidget(2L, "VISITS", "LINE_CHART")
        ));

        mockMvc.perform(get("/api/v1/dashboard/widgets")
                        .param("widgetType", "BAR_CHART")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("PV_BY_FEATURE"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:WIDGET:READ"})
    @DisplayName("GET /dashboard/widgets/{id} — 위젯 단건 조회 200 OK")
    void get_returnsOkWithWidget() throws Exception {
        when(service.getById(eq(7L))).thenReturn(sampleWidget(7L, "WIDGET_7", "BAR_CHART"));

        mockMvc.perform(get("/api/v1/dashboard/widgets/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.code").value("WIDGET_7"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /dashboard/widgets — 위젯 생성 200 OK (SUPER_ADMIN)")
    void create_returnsOkWithWidget() throws Exception {
        WidgetRequest req = new WidgetRequest(
                "NEW_WIDGET", "신규 위젯", "설명",
                "BAR_CHART", "KPI_CACHE", "{}", "{}",
                List.of("period"), List.of("DEPT_ADMIN"), "ACTIVE"
        );
        when(service.create(any(WidgetRequest.class), any()))
                .thenReturn(sampleWidget(20L, "NEW_WIDGET", "BAR_CHART"));

        mockMvc.perform(post("/api/v1/dashboard/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.code").value("NEW_WIDGET"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /dashboard/widgets — 필수 필드(code) 누락 시 400 Bad Request")
    void create_missingCode_returns400() throws Exception {
        // code(@NotBlank) 누락
        String invalidJson = "{\"name\":\"이름\",\"widgetType\":\"BAR_CHART\","
                + "\"dataSource\":\"KPI_CACHE\",\"dataSourceConfig\":\"{}\"}";

        mockMvc.perform(post("/api/v1/dashboard/widgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"DEPT_ADMIN"})
    @DisplayName("PUT /dashboard/widgets/{id} — 위젯 수정 200 OK (DEPT_ADMIN)")
    void update_returnsOkWithUpdatedWidget() throws Exception {
        WidgetRequest req = new WidgetRequest(
                "UPDATED", "수정된 위젯", "설명",
                "LINE_CHART", "KPI_CACHE", "{}", "{}",
                List.of("period"), List.of("DEPT_ADMIN"), "ACTIVE"
        );
        when(service.update(eq(5L), any(WidgetRequest.class)))
                .thenReturn(sampleWidget(5L, "UPDATED", "LINE_CHART"));

        mockMvc.perform(put("/api/v1/dashboard/widgets/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.code").value("UPDATED"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("DELETE /dashboard/widgets/{id} — 위젯 삭제 204 No Content")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/widgets/3"))
                .andExpect(status().isNoContent());

        verify(service).delete(3L);
    }

    @Test
    @WithMockUser(roles = {"DEPT_ADMIN"})
    @DisplayName("POST /dashboard/widgets/preview — 미리보기 200 OK")
    void preview_returnsOkWithData() throws Exception {
        WidgetRequest req = new WidgetRequest(
                "PREVIEW", "미리보기", "설명",
                "BAR_CHART", "KPI_CACHE", "{}", "{}",
                List.of("period"), List.of("DEPT_ADMIN"), "ACTIVE"
        );
        when(service.preview(any(WidgetRequest.class), anyList()))
                .thenReturn(sampleData(0L, "PREVIEW", "BAR_CHART"));

        mockMvc.perform(post("/api/v1/dashboard/widgets/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widget.code").value("PREVIEW"))
                .andExpect(jsonPath("$.dataset.categories.length()").value(3));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:WIDGET:READ"})
    @DisplayName("GET /dashboard/widgets/{id}/data — 위젯 차트 데이터 200 OK")
    void getData_returnsOkWithDataset() throws Exception {
        when(service.getData(eq(11L), anyMap(), anyList()))
                .thenReturn(sampleData(11L, "PV_BY_FEATURE", "BAR_CHART"));

        mockMvc.perform(get("/api/v1/dashboard/widgets/11/data")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-07")
                        .param("dim", "feature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widget.id").value(11))
                .andExpect(jsonPath("$.cacheHit").value(true))
                .andExpect(jsonPath("$.dataset.series[0].name").value("PV"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:WIDGET:READ"})
    @DisplayName("GET /dashboard/widgets/{id}/data/series — 시계열 데이터 200 OK")
    void getSeries_returnsOkWithSeries() throws Exception {
        when(service.getData(eq(12L), anyMap(), anyList()))
                .thenReturn(sampleData(12L, "VISITS_TS", "LINE_CHART"));

        mockMvc.perform(get("/api/v1/dashboard/widgets/12/data/series")
                        .param("dim", "period")
                        .param("group", "feature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widget.id").value(12))
                .andExpect(jsonPath("$.dataset.series.length()").value(1));
    }

    @Test
    @DisplayName("DELETE /dashboard/widgets/{id} — 인증 없이 접근 시 403 Forbidden")
    void delete_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/widgets/3"))
                .andExpect(status().isForbidden());
    }
}
