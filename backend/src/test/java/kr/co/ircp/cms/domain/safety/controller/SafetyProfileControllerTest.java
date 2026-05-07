package kr.co.ircp.cms.domain.safety.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.safety.dto.MatchRequest;
import kr.co.ircp.cms.domain.safety.dto.MatchResponse;
import kr.co.ircp.cms.domain.safety.dto.MatchedIncident;
import kr.co.ircp.cms.domain.safety.dto.ProfileResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileUpsertRequest;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.service.CompanySafetyProfileService;
import kr.co.ircp.cms.domain.safety.service.SafetyMatchingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SafetyProfileController @WebMvcTest (GREEN 단계).
 *
 * <p>REQ-SAFETY-002 — 기업 안전 프로필 + 매칭 HTTP 계층 검증.
 * {@code @AuthenticationPrincipal Long companyId} 주입 동작 확인.
 */
@WebMvcTest(SafetyProfileController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SafetyProfileController GREEN 테스트 (REQ-SAFETY-002)")
class SafetyProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CompanySafetyProfileService profileService;

    @MockitoBean
    private SafetyMatchingService matchingService;

    private static final Long COMPANY_ID = 100L;

    private static ProfileResponse sampleProfile() {
        return new ProfileResponse(
                1L, COMPANY_ID, "C20", "건설업", 50,
                "건설 시공", List.of("HF_FALL", "HF_FIRE"),
                new BigDecimal("0.75"), "HIGH", Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/safety/profiles — 프로필 upsert 200 OK")
    void upsert_returnsOk() throws Exception {
        ProfileUpsertRequest request = new ProfileUpsertRequest(
                "C20", "건설업", 50, "건설 시공",
                List.of("HF_FALL", "HF_FIRE"), "HIGH"
        );
        when(profileService.upsertProfile(eq(COMPANY_ID), any(ProfileUpsertRequest.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(post("/api/v1/safety/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID))
                .andExpect(jsonPath("$.industryCode").value("C20"))
                .andExpect(jsonPath("$.riskGrade").value("HIGH"));
    }

    @Test
    @DisplayName("POST /api/v1/safety/profiles — 필수 필드(industryCode) 누락 시 400")
    void upsert_missingIndustryCode_returns400() throws Exception {
        // industryCode (@NotBlank) 누락
        String invalidJson = "{\"subIndustry\":\"건설업\",\"employeeCount\":10}";

        mockMvc.perform(post("/api/v1/safety/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/safety/profiles/me — 본인 프로필 200 OK")
    void getMyProfile_returnsOk() throws Exception {
        when(profileService.getMyProfile(eq(COMPANY_ID))).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/safety/profiles/me")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID))
                .andExpect(jsonPath("$.riskGrade").value("HIGH"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/profiles/me — 미존재 시 404")
    void getMyProfile_notFound_returns404() throws Exception {
        when(profileService.getMyProfile(eq(COMPANY_ID)))
                .thenThrow(new SafetyProfileNotFoundException(COMPANY_ID));

        mockMvc.perform(get("/api/v1/safety/profiles/me")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/safety/match — 매칭 실행 200 OK")
    void match_returnsOk() throws Exception {
        MatchedIncident incident = new MatchedIncident(
                10L, "C20", "FALL", "FATAL",
                Instant.now(), "추락 사고", new BigDecimal("0.85"), "{\"explain_ko\":\"높은 일치도\"}"
        );
        MatchResponse response = new MatchResponse(1L, 5, false, List.of(incident));
        when(matchingService.matchForCompany(eq(COMPANY_ID), eq(5))).thenReturn(response);

        MatchRequest request = new MatchRequest(5);

        mockMvc.perform(post("/api/v1/safety/match")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.topN").value(5))
                .andExpect(jsonPath("$.fromCache").value(false))
                .andExpect(jsonPath("$.results[0].incidentId").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/safety/match/{profileId}/cached — 캐시 결과 200 OK")
    void getCached_returnsOk() throws Exception {
        MatchResponse response = new MatchResponse(1L, 5, true, List.of());
        when(matchingService.getCachedForProfile(eq(1L), eq(5))).thenReturn(response);

        mockMvc.perform(get("/api/v1/safety/match/1/cached")
                        .param("topN", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(1))
                .andExpect(jsonPath("$.fromCache").value(true));
    }

    // ─── 헬퍼: principal로 Long(companyId) 직접 사용 ───────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken companyAuth(
            Long companyId) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                companyId, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
