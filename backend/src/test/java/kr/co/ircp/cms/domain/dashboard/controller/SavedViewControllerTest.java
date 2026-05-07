package kr.co.ircp.cms.domain.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewRequest;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewResponse;
import kr.co.ircp.cms.domain.dashboard.service.SavedViewService;
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
 * SavedViewController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-008 REQ-DASHBOARD-004 (REQ-VIZ-004):
 * 사용자 맞춤 뷰(필터 상태) CRUD + 적용(last_used_at 갱신) HTTP 계층 검증.
 */
@WebMvcTest(SavedViewController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SavedViewController GREEN 테스트 (REQ-DASHBOARD-004)")
class SavedViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SavedViewService service;

    private static SavedViewResponse sample(Long id, String name, boolean isDefault) {
        return new SavedViewResponse(
                id, 1L, 100L, name, "설명",
                "{\"period\":\"7d\"}", isDefault, false,
                List.of("DEPT_ADMIN"),
                Instant.now(), Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:VIEW:READ"})
    @DisplayName("GET /dashboard/views — 사용자 저장 뷰 목록 200 OK")
    void list_returnsOkWithViews() throws Exception {
        when(service.listForUser(any(), eq(100L))).thenReturn(List.of(
                sample(1L, "기본 뷰", true),
                sample(2L, "주간 뷰", false)
        ));

        mockMvc.perform(get("/api/v1/dashboard/views").param("dashboard_id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("기본 뷰"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:VIEW:WRITE"})
    @DisplayName("POST /dashboard/views — 저장 뷰 생성 200 OK")
    void create_returnsOkWithView() throws Exception {
        SavedViewRequest req = new SavedViewRequest(
                100L, "신규 뷰", "설명",
                "{\"period\":\"30d\"}", false, false, List.of()
        );
        when(service.create(any(), any(SavedViewRequest.class)))
                .thenReturn(sample(11L, "신규 뷰", false));

        mockMvc.perform(post("/api/v1/dashboard/views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.name").value("신규 뷰"));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:VIEW:WRITE"})
    @DisplayName("POST /dashboard/views — name 누락 시 400 Bad Request")
    void create_missingName_returns400() throws Exception {
        // name(@NotBlank) + filterState(@NotNull) 누락 검증
        String invalidJson = "{\"description\":\"설명\"}";

        mockMvc.perform(post("/api/v1/dashboard/views")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:VIEW:WRITE"})
    @DisplayName("PUT /dashboard/views/{id} — 저장 뷰 수정 200 OK")
    void update_returnsOkWithUpdatedView() throws Exception {
        SavedViewRequest req = new SavedViewRequest(
                100L, "수정된 뷰", "수정", "{\"period\":\"14d\"}",
                true, false, List.of()
        );
        when(service.update(eq(5L), any(), any(SavedViewRequest.class)))
                .thenReturn(sample(5L, "수정된 뷰", true));

        mockMvc.perform(put("/api/v1/dashboard/views/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("수정된 뷰"))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:VIEW:WRITE"})
    @DisplayName("DELETE /dashboard/views/{id} — 저장 뷰 삭제 204 No Content")
    void delete_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/views/3"))
                .andExpect(status().isNoContent());

        verify(service).delete(eq(3L), any());
    }

    @Test
    @WithMockUser(authorities = {"DASHBOARD:VIEW:READ"})
    @DisplayName("POST /dashboard/views/{id}/apply — 뷰 적용 + last_used_at 갱신 200 OK")
    void apply_returnsOkWithAppliedView() throws Exception {
        when(service.apply(eq(7L), any())).thenReturn(sample(7L, "적용된 뷰", false));

        mockMvc.perform(post("/api/v1/dashboard/views/7/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("적용된 뷰"));
    }
}
