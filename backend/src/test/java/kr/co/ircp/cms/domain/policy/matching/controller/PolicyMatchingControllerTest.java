package kr.co.ircp.cms.domain.policy.matching.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.policy.matching.dto.CompanyProfileUpsertRequest;
import kr.co.ircp.cms.domain.policy.matching.dto.MatchedPolicy;
import kr.co.ircp.cms.domain.policy.matching.dto.PolicyMatchResponse;
import kr.co.ircp.cms.domain.policy.matching.exception.CompanyMatchInputNotFoundException;
import kr.co.ircp.cms.domain.policy.matching.service.PolicyMatchingService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PolicyMatchingController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-007 REQ-POLICY-002: 기업 프로필 → 정책 매칭 (TOP N) HTTP 계층 검증.
 */
@WebMvcTest(PolicyMatchingController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PolicyMatchingController GREEN 테스트 (REQ-POLICY-002)")
class PolicyMatchingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PolicyMatchingService matchingService;

    private static PolicyMatchResponse sampleMatch(Long companyId, int topN, boolean fromCache) {
        MatchedPolicy m1 = new MatchedPolicy(
                100L, "청년창업지원", "중기부",
                Instant.parse("2026-06-30T23:59:59Z"),
                new BigDecimal("87.50"), "A",
                "{\"industry\":30,\"region\":20}",
                Instant.now()
        );
        MatchedPolicy m2 = new MatchedPolicy(
                200L, "혁신성장지원", "산업부",
                Instant.parse("2026-07-31T23:59:59Z"),
                new BigDecimal("72.00"), "B",
                "{\"industry\":25,\"region\":15}",
                Instant.now()
        );
        return new PolicyMatchResponse(companyId, topN, fromCache, List.of(m1, m2));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:MATCH:RUN"})
    @DisplayName("POST /policy/match — 매칭 실행 200 OK")
    void match_returnsOkWithResults() throws Exception {
        when(matchingService.matchForCompany(eq(42L), eq(10)))
                .thenReturn(sampleMatch(42L, 10, false));

        mockMvc.perform(post("/api/v1/policy/match")
                        .param("companyId", "42")
                        .param("topN", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(42))
                .andExpect(jsonPath("$.topN").value(10))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].grade").value("A"));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:MATCH:RUN"})
    @DisplayName("POST /policy/match — 기업 프로필 미존재 시 404 Not Found")
    void match_companyNotFound_returns404() throws Exception {
        when(matchingService.matchForCompany(eq(999L), eq(10)))
                .thenThrow(new CompanyMatchInputNotFoundException(999L));

        mockMvc.perform(post("/api/v1/policy/match")
                        .param("companyId", "999")
                        .param("topN", "10"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = {"POLICY:MATCH:READ"})
    @DisplayName("GET /policy/match/results — 캐시된 결과 조회 200 OK")
    void getCachedResults_returnsOkFromCache() throws Exception {
        when(matchingService.getCachedResults(eq(42L), eq(10)))
                .thenReturn(sampleMatch(42L, 10, true));

        mockMvc.perform(get("/api/v1/policy/match/results")
                        .param("companyId", "42")
                        .param("topN", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.results.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:PROFILE:WRITE"})
    @DisplayName("PUT /policy/company-profile — 기업 프로필 등록·수정 204 No Content")
    void upsertProfile_returnsNoContent() throws Exception {
        CompanyProfileUpsertRequest req = new CompanyProfileUpsertRequest(
                42L,
                List.of("IT", "바이오"),
                List.of("서울"),
                25, 1_000_000_000L, 24,
                List.of("ISO9001"),
                "{\"focus\":\"AI\"}"
        );

        mockMvc.perform(put("/api/v1/policy/company-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(matchingService).upsertCompanyProfile(any(CompanyProfileUpsertRequest.class));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:PROFILE:WRITE"})
    @DisplayName("PUT /policy/company-profile — companyId(@NotNull) 누락 시 400 Bad Request")
    void upsertProfile_missingCompanyId_returns400() throws Exception {
        // companyId(@NotNull) 누락
        String invalidJson = "{\"industryCodes\":[\"IT\"],\"regionCodes\":[\"서울\"]}";

        mockMvc.perform(put("/api/v1/policy/company-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
