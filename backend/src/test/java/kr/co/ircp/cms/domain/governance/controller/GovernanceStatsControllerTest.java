package kr.co.ircp.cms.domain.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.governance.dto.StatsRecomputeRequest;
import kr.co.ircp.cms.domain.governance.service.GovernanceStatsService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GovernanceStatsController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001~004: 거버넌스 통계 조회·재계산 HTTP 계층 검증.
 */
@WebMvcTest(GovernanceStatsController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("GovernanceStatsController GREEN 테스트 (REQ-DATA-001~004)")
class GovernanceStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GovernanceStatsService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /stats/boards — 일별 통계 조회 200 OK")
    void boards_dailyPeriod_returnsOk() throws Exception {
        // given — period=daily 시 findBoardDaily 호출
        when(service.findBoardDaily(any(), any(), any())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/governance/stats/boards")
                        .param("period", "daily")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-07"))
                .andExpect(status().isOk());

        verify(service).findBoardDaily(any(), eq(LocalDate.parse("2026-04-01")),
                eq(LocalDate.parse("2026-04-07")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /stats/boards — 월별 통계 조회 200 OK")
    void boards_monthlyPeriod_returnsOk() throws Exception {
        // given
        when(service.findBoardMonthly(any(), any(), any())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/governance/stats/boards")
                        .param("period", "monthly"))
                .andExpect(status().isOk());

        verify(service).findBoardMonthly(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /stats/contents — 콘텐츠 통계 조회 200 OK")
    void contents_returnsOk() throws Exception {
        when(service.findContentDaily(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/governance/stats/contents")
                        .param("contentId", "55"))
                .andExpect(status().isOk());

        verify(service).findContentDaily(eq(55L), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /stats/policies — 정책 매칭 통계 조회 200 OK")
    void policies_returnsOk() throws Exception {
        when(service.findPolicyStats(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/governance/stats/policies"))
                .andExpect(status().isOk());

        verify(service).findPolicyStats(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /stats/safety — 안전 통계 조회 200 OK")
    void safety_returnsOk() throws Exception {
        when(service.findSafetyStats(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/governance/stats/safety")
                        .param("category", "INCIDENT"))
                .andExpect(status().isOk());

        verify(service).findSafetyStats(eq("INCIDENT"), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /stats/recompute — 수동 재계산 200 OK + 처리 결과 반환")
    void recompute_returnsOkWithProcessed() throws Exception {
        // given
        StatsRecomputeRequest req = new StatsRecomputeRequest(
                "BoardStatsDailyJob",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-01"));
        when(service.recompute(eq("BoardStatsDailyJob"), any(), any()))
                .thenReturn(Map.of("job", "BoardStatsDailyJob", "processed", 100));

        // when & then
        mockMvc.perform(post("/api/v1/governance/stats/recompute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("BoardStatsDailyJob"))
                .andExpect(jsonPath("$.processed").value(100));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /stats/recompute — job 누락 시 400 Bad Request")
    void recompute_missingJob_returns400() throws Exception {
        // given — job 누락
        String invalid = "{\"from\":\"2026-04-01\",\"to\":\"2026-04-01\"}";

        // when & then
        mockMvc.perform(post("/api/v1/governance/stats/recompute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());
    }
}
