package kr.co.ircp.cms.domain.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutRequest;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutResponse;
import kr.co.ircp.cms.domain.dashboard.service.DashboardLayoutService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
 * DashboardLayoutController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-008 REQ-DASHBOARD-002 (REQ-VIZ-002): 레이아웃 CRUD + 클론 + 기본 지정 HTTP 계층 검증.
 */
@WebMvcTest(DashboardLayoutController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("DashboardLayoutController GREEN 테스트 (REQ-DASHBOARD-002)")
class DashboardLayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DashboardLayoutService service;

    private static LayoutResponse sample(Long id, String name, boolean isDefault) {
        return new LayoutResponse(
                id, 1L, name, "설명", isDefault,
                "{\"cols\":12}", List.of("DEPT_ADMIN"),
                List.of(),
                Instant.now(), Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:READ"})
    @DisplayName("GET /dashboard/layouts — 사용자 레이아웃 목록 200 OK")
    void list_returnsOkWithLayouts() throws Exception {
        when(service.listForUser(any(), anyList())).thenReturn(List.of(
                sample(1L, "내 레이아웃", true),
                sample(2L, "공유 레이아웃", false)
        ));

        mockMvc.perform(get("/api/v1/dashboard/layouts").param("roles", "DEPT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("내 레이아웃"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:READ"})
    @DisplayName("GET /dashboard/layouts/{id} — 단건 조회 200 OK")
    void get_returnsOkWithLayout() throws Exception {
        when(service.getById(eq(7L))).thenReturn(sample(7L, "테스트 레이아웃", false));

        mockMvc.perform(get("/api/v1/dashboard/layouts/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("테스트 레이아웃"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:WRITE"})
    @DisplayName("POST /dashboard/layouts — 레이아웃 생성 200 OK + body")
    void create_returnsOkWithLayout() throws Exception {
        LayoutRequest req = new LayoutRequest("새 레이아웃", "설명", "{\"cols\":12}",
                List.of("DEPT_ADMIN"), List.of());
        when(service.create(any(), any(LayoutRequest.class)))
                .thenReturn(sample(10L, "새 레이아웃", false));

        mockMvc.perform(post("/api/v1/dashboard/layouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("새 레이아웃"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:WRITE"})
    @DisplayName("POST /dashboard/layouts — name 필수 누락 시 400 Bad Request")
    void create_missingName_returns400() throws Exception {
        // name(@NotBlank) 누락
        String invalidJson = "{\"description\":\"설명\"}";

        mockMvc.perform(post("/api/v1/dashboard/layouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:WRITE"})
    @DisplayName("PUT /dashboard/layouts/{id} — 레이아웃 수정 200 OK")
    void update_returnsOkWithUpdatedLayout() throws Exception {
        LayoutRequest req = new LayoutRequest("수정된 이름", "수정 설명", null, null, null);
        when(service.update(eq(5L), any(), any(LayoutRequest.class)))
                .thenReturn(sample(5L, "수정된 이름", false));

        mockMvc.perform(put("/api/v1/dashboard/layouts/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("수정된 이름"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:WRITE"})
    @DisplayName("DELETE /dashboard/layouts/{id} — 레이아웃 삭제 204 No Content")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/layouts/3"))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(3L), any());
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:WRITE"})
    @DisplayName("POST /dashboard/layouts/{id}/clone — 레이아웃 deep-copy 200 OK")
    void clone_returnsOkWithClonedLayout() throws Exception {
        when(service.clone(eq(7L), any())).thenReturn(sample(99L, "복제본", false));

        mockMvc.perform(post("/api/v1/dashboard/layouts/7/clone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.name").value("복제본"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:LAYOUT:WRITE"})
    @DisplayName("PUT /dashboard/layouts/{id}/default — 기본 레이아웃 지정 204 No Content")
    void setDefault_returnsNoContent() throws Exception {
        mockMvc.perform(put("/api/v1/dashboard/layouts/4/default"))
                .andExpect(status().isNoContent());

        verify(service).setDefault(eq(4L), any());
    }
}
