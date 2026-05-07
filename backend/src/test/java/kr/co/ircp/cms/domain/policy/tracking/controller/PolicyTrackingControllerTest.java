package kr.co.ircp.cms.domain.policy.tracking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.policy.tracking.dto.ConversionStats;
import kr.co.ircp.cms.domain.policy.tracking.dto.TrackEventRequest;
import kr.co.ircp.cms.domain.policy.tracking.service.PolicyTrackingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PolicyTrackingController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-007 REQ-POLICY-005: 정책 신청·클릭 추적 (POLICY_APPLY_CVR) HTTP 계층 검증.
 */
@WebMvcTest(PolicyTrackingController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PolicyTrackingController GREEN 테스트 (REQ-POLICY-005)")
class PolicyTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicyTrackingService trackingService;

    @Test
    @WithMockUser(authorities = {"POLICY:TRACK:WRITE"})
    @DisplayName("POST /policy/programs/{id}/track — 클릭/뷰 이벤트 추적 204 No Content")
    void trackEvent_returnsNoContent() throws Exception {
        TrackEventRequest req = new TrackEventRequest(
                "DASHBOARD", "CLICK_APPLY", 12345L,
                "Mozilla/5.0", "127.0.0.1"
        );

        mockMvc.perform(post("/api/v1/policy/programs/100/track")
                        .param("userId", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(trackingService).trackEvent(eq(42L), eq(100L), any(TrackEventRequest.class));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:TRACK:WRITE"})
    @DisplayName("POST /policy/programs/{id}/track — VIEW 이벤트 추적 204 No Content")
    void trackEvent_viewAction_returnsNoContent() throws Exception {
        TrackEventRequest req = new TrackEventRequest(
                "EMAIL", "VIEW", null,
                "Mozilla/5.0", "10.0.0.1"
        );

        mockMvc.perform(post("/api/v1/policy/programs/200/track")
                        .param("userId", "99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(trackingService).trackEvent(eq(99L), eq(200L), any(TrackEventRequest.class));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:TRACK:WRITE"})
    @DisplayName("POST /policy/programs/{id}/track — 필수 필드(action) 누락 시 400 Bad Request")
    void trackEvent_missingAction_returns400() throws Exception {
        // action(@NotBlank) 누락
        String invalidJson = "{\"source\":\"DASHBOARD\"}";

        mockMvc.perform(post("/api/v1/policy/programs/100/track")
                        .param("userId", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"POLICY:STATS:READ"})
    @DisplayName("GET /policy/admin/stats/conversion — 정책별 전환 통계 200 OK")
    void getConversionStats_returnsOk() throws Exception {
        ConversionStats stats = ConversionStats.compute(100L, 1000L, 250L, 100L, 50L);
        when(trackingService.getConversionStats(eq(100L))).thenReturn(stats);

        mockMvc.perform(get("/api/v1/policy/admin/stats/conversion")
                        .param("policyId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(100))
                .andExpect(jsonPath("$.viewCount").value(1000))
                .andExpect(jsonPath("$.clickCount").value(250))
                .andExpect(jsonPath("$.redirectCount").value(100))
                .andExpect(jsonPath("$.savedCount").value(50));
    }
}
