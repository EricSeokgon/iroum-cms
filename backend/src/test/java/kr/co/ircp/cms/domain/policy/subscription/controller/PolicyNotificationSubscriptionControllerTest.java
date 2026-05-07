package kr.co.ircp.cms.domain.policy.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionEntry;
import kr.co.ircp.cms.domain.policy.subscription.dto.SubscriptionUpdateRequest;
import kr.co.ircp.cms.domain.policy.subscription.service.PolicyNotificationSubscriptionService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PolicyNotificationSubscriptionController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-007 REQ-POLICY-004: 정책 알림 수신 동의(채널×카테고리) HTTP 계층 검증.
 */
@WebMvcTest(PolicyNotificationSubscriptionController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PolicyNotificationSubscriptionController GREEN 테스트 (REQ-POLICY-004)")
class PolicyNotificationSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PolicyNotificationSubscriptionService subscriptionService;

    @Test
    @WithMockUser(authorities = {"POLICY:SUBSCRIPTION:READ"})
    @DisplayName("GET /policy/subscriptions/me — 내 수신 동의 목록 200 OK")
    void getMine_returnsOkWithEntries() throws Exception {
        when(subscriptionService.getMySubscriptions(eq(42L)))
                .thenReturn(List.of(
                        new SubscriptionEntry("EMAIL", "POLICY", true),
                        new SubscriptionEntry("PUSH", "POLICY", false),
                        new SubscriptionEntry("SMS", "POLICY", true)
                ));

        mockMvc.perform(get("/api/v1/policy/subscriptions/me")
                        .param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].optedIn").value(true))
                .andExpect(jsonPath("$[1].optedIn").value(false));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:SUBSCRIPTION:READ"})
    @DisplayName("GET /policy/subscriptions/me — 동의 항목 없는 사용자 빈 배열 200 OK")
    void getMine_noEntries_returnsEmptyList() throws Exception {
        when(subscriptionService.getMySubscriptions(eq(99L)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/policy/subscriptions/me")
                        .param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:SUBSCRIPTION:WRITE"})
    @DisplayName("PUT /policy/subscriptions/me — 수신 동의 일괄 업데이트 204 No Content")
    void updateMine_returnsNoContent() throws Exception {
        SubscriptionUpdateRequest req = new SubscriptionUpdateRequest(List.of(
                new SubscriptionEntry("EMAIL", "POLICY", true),
                new SubscriptionEntry("PUSH", "POLICY", false)
        ));

        mockMvc.perform(put("/api/v1/policy/subscriptions/me")
                        .param("userId", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(subscriptionService).updateMySubscriptions(eq(42L), any(SubscriptionUpdateRequest.class));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:SUBSCRIPTION:WRITE"})
    @DisplayName("PUT /policy/subscriptions/me — entries(@NotEmpty) 빈 배열일 때 400 Bad Request")
    void updateMine_emptyEntries_returns400() throws Exception {
        // entries(@NotEmpty) 빈 배열
        String invalidJson = "{\"entries\":[]}";

        mockMvc.perform(put("/api/v1/policy/subscriptions/me")
                        .param("userId", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
