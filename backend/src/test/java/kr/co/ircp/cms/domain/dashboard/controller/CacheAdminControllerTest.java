package kr.co.ircp.cms.domain.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.dashboard.dto.CacheInvalidateRequest;
import kr.co.ircp.cms.domain.dashboard.dto.CacheStatsResponse;
import kr.co.ircp.cms.domain.dashboard.service.CacheAdminService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CacheAdminController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-008 REQ-DASHBOARD-005 (REQ-VIZ-005-D-5):
 * Caffeine 캐시 무효화 + 통계 조회 HTTP 계층 검증.
 */
@WebMvcTest(CacheAdminController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("CacheAdminController GREEN 테스트 (REQ-DASHBOARD-005)")
class CacheAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CacheAdminService service;

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /dashboard/cache/invalidate — 특정 위젯 캐시 무효화 204 No Content")
    void invalidate_widgets_returnsNoContent() throws Exception {
        CacheInvalidateRequest req = new CacheInvalidateRequest(
                List.of(1L, 2L), null, false
        );

        mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(service).invalidate(any(CacheInvalidateRequest.class));
    }

    @Test
    @WithMockUser(roles = {"DEPT_ADMIN"})
    @DisplayName("POST /dashboard/cache/invalidate — 전체 무효화 (all=true) 204 No Content")
    void invalidate_all_returnsNoContent() throws Exception {
        CacheInvalidateRequest req = new CacheInvalidateRequest(null, null, true);

        mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(service).invalidate(any(CacheInvalidateRequest.class));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("GET /dashboard/cache/stats — 캐시 통계 조회 200 OK")
    void stats_returnsOkWithStatistics() throws Exception {
        when(service.stats()).thenReturn(new CacheStatsResponse(125L, 7L));

        mockMvc.perform(get("/api/v1/dashboard/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEntries").value(125))
                .andExpect(jsonPath("$.expiredEntries").value(7));
    }

    @Test
    @WithMockUser(roles = {"DEPT_ADMIN"})
    @DisplayName("GET /dashboard/cache/stats — 0건 통계 조회 200 OK")
    void stats_empty_returnsOk() throws Exception {
        when(service.stats()).thenReturn(new CacheStatsResponse(0L, 0L));

        mockMvc.perform(get("/api/v1/dashboard/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeEntries").value(0))
                .andExpect(jsonPath("$.expiredEntries").value(0));
    }
}
