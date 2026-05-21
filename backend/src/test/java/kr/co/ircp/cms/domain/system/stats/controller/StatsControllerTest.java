package kr.co.ircp.cms.domain.system.stats.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.system.stats.dto.TopPageResponse;
import kr.co.ircp.cms.domain.system.stats.dto.TrendItemResponse;
import kr.co.ircp.cms.domain.system.stats.mapper.AccessStatDailyMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StatsController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-005 REQ-SYSTEM-002-D, REQ-SYSTEM-003-D: 접속 통계 추이/Top Pages/재집계 HTTP 계층 검증.
 */
@WebMvcTest(StatsController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("StatsController GREEN 테스트 (REQ-SYSTEM-002-D, REQ-SYSTEM-003-D)")
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StatsService statsService;

    // StatsController는 AccessStatDailyMapper도 주입받으므로 슬라이스에서 @MockitoBean으로 대체
    @MockitoBean
    private AccessStatDailyMapper accessStatDailyMapper;

    @Test
    @WithMockUser(authorities = {"SYSTEM:STATS"})
    @DisplayName("GET /stats/trend — 30일 추이 200 OK + 응답 배열")
    void trend_returnsOkWithTrendItems() throws Exception {
        TrendItemResponse t1 = new TrendItemResponse(LocalDate.of(2026, 5, 1), 100, 250, 2);
        TrendItemResponse t2 = new TrendItemResponse(LocalDate.of(2026, 5, 2), 120, 300, 1);
        when(statsService.getTrend30Days(anyLong())).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/v1/system/stats/trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].visits").value(100))
                .andExpect(jsonPath("$[1].pageViews").value(300));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:STATS"})
    @DisplayName("GET /stats/top-pages — 기본 7일 Top Pages 200 OK")
    void topPages_default7days_returnsOk() throws Exception {
        TopPageResponse p1 = new TopPageResponse("/home", 1500L, null, null, 1);
        TopPageResponse p2 = new TopPageResponse("/about", 800L, null, null, 2);
        when(statsService.getTopPages(eq(7), anyLong())).thenReturn(List.of(p1, p2));

        // 응답 DTO는 @JsonNaming(SnakeCaseStrategy)로 직렬화되므로 JSON 경로도 snake_case 사용
        mockMvc.perform(get("/api/v1/system/stats/top-pages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].page_url").value("/home"))
                .andExpect(jsonPath("$[0].rank").value(1));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:STATS"})
    @DisplayName("GET /stats/top-pages?days=30 — 30일 Top Pages 200 OK")
    void topPages_30days_returnsOk() throws Exception {
        TopPageResponse p1 = new TopPageResponse("/board", 5000L, null, null, 1);
        when(statsService.getTopPages(eq(30), anyLong())).thenReturn(List.of(p1));

        // TopPageResponse DTO는 views 필드를 사용 (count → views 리네이밍됨)
        mockMvc.perform(get("/api/v1/system/stats/top-pages").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].views").value(5000));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:STATS"})
    @DisplayName("POST /stats/recompute — 수동 재집계 200 OK + 메시지 반환")
    void recompute_returnsOkWithMessage() throws Exception {
        mockMvc.perform(post("/api/v1/system/stats/recompute")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("재집계 완료"))
                .andExpect(jsonPath("$.from").value("2026-05-01"))
                .andExpect(jsonPath("$.to").value("2026-05-07"));

        verify(statsService).recompute(any(LocalDate.class), any(LocalDate.class), anyLong());
    }

    @Test
    @DisplayName("POST /stats/recompute — 인증 없이 접근 시 403 Forbidden")
    void recompute_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/system/stats/recompute")
                        .param("from", "2026-05-01")
                        .param("to", "2026-05-07"))
                .andExpect(status().isForbidden());
    }
}
