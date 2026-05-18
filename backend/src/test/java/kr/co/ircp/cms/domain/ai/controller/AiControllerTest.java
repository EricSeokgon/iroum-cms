package kr.co.ircp.cms.domain.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.ai.dto.AiMetricDto;
import kr.co.ircp.cms.domain.ai.dto.GrowthStageResultDto;
import kr.co.ircp.cms.domain.ai.dto.RiskScoreResultDto;
import kr.co.ircp.cms.domain.ai.dto.SimulationResultDto;
import kr.co.ircp.cms.domain.ai.exception.AiSimulationNotFoundException;
import kr.co.ircp.cms.domain.ai.mapper.AiRetrainQueueMapper;
import kr.co.ircp.cms.domain.ai.mapper.AiSimulationSessionMapper;
import kr.co.ircp.cms.domain.ai.service.AiModelMetricService;
import kr.co.ircp.cms.domain.ai.service.GrowthStageService;
import kr.co.ircp.cms.domain.ai.service.RiskScoreService;
import kr.co.ircp.cms.domain.ai.service.SimulationService;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.infra.ml.MlServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 컨트롤러 HTTP 계층 테스트 (RED).
 *
 * <p>SPEC-CMS-AI-001 Step 2 — growth-stage/simulation/risk-score 공개 + admin 권한.
 * ADMIN 전용 엔드포인트는 인증되어도 ROLE=ADMIN 없으면 403.
 */
@WebMvcTest({GrowthStageController.class, SimulationController.class,
        RiskScoreController.class, AiAdminController.class})
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("AI Controllers GREEN 테스트 (SPEC-CMS-AI-001)")
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GrowthStageService growthStageService;

    @MockitoBean
    private SimulationService simulationService;

    @MockitoBean
    private RiskScoreService riskScoreService;

    @MockitoBean
    private AiModelMetricService aiModelMetricService;

    @MockitoBean
    private AiRetrainQueueMapper aiRetrainQueueMapper;

    @MockitoBean
    private AiSimulationSessionMapper aiSimulationSessionMapper;

    @MockitoBean
    private MlServiceClient mlServiceClient;

    private static final JwtPrincipal USER_PRINCIPAL =
            new JwtPrincipal(10L, "user", Set.of("USER"));
    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("ADMIN"));

    private UsernamePasswordAuthenticationToken jwtAuth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.getAuthorities().stream()
                        .map(a -> (GrantedAuthority) a)
                        .toList());
    }

    @Test
    @DisplayName("GET /api/v1/ai/growth-stage — 인증 사용자 200 OK + stage 반환")
    void growthStage_returns200() throws Exception {
        when(growthStageService.predict(any()))
                .thenReturn(new GrowthStageResultDto("GROWTH",
                        java.util.Map.of("GROWTH", 0.62), 0.62, "v1", false));

        mockMvc.perform(get("/api/v1/ai/growth-stage")
                        .param("ksicCode", "J62010")
                        .param("capitalAmount", "100000000")
                        .param("foundingYear", "2020")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("GROWTH"));
    }

    @Test
    @DisplayName("POST /api/v1/ai/simulation/start — 201 Created + sessionId 반환")
    void simulationStart_returns201() throws Exception {
        UUID sid = UUID.randomUUID();
        when(simulationService.start(any(), anyString()))
                .thenReturn(new SimulationResultDto(sid, "NONE", "{\"projection\":[]}"));

        mockMvc.perform(post("/api/v1/ai/simulation/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ksicCode\":\"J62010\",\"capitalAmount\":100000000,"
                                + "\"foundingYear\":2020}")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sid.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/ai/simulation/{id} — 존재 시 200 OK")
    void simulationGet_returns200() throws Exception {
        UUID sid = UUID.randomUUID();
        when(simulationService.getResult(sid))
                .thenReturn(new SimulationResultDto(sid, "NONE", "{\"projection\":[]}"));

        mockMvc.perform(get("/api/v1/ai/simulation/" + sid)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sid.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/ai/simulation/{id} — 미존재/만료 시 404 + AI_SIMULATION_NOT_FOUND")
    void simulationGet_notFound_returns404() throws Exception {
        UUID sid = UUID.randomUUID();
        when(simulationService.getResult(sid))
                .thenThrow(new AiSimulationNotFoundException(sid));

        mockMvc.perform(get("/api/v1/ai/simulation/" + sid)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AI_SIMULATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/ai/risk-score — 200 OK + riskGrade enum 값")
    void riskScore_returns200() throws Exception {
        when(riskScoreService.score(any()))
                .thenReturn(new RiskScoreResultDto(0.18, "YELLOW", List.of(), "v1"));

        mockMvc.perform(get("/api/v1/ai/risk-score")
                        .param("ksicCode", "J62010")
                        .param("capitalAmount", "100000000")
                        .param("foundingYear", "2020")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskGrade").value("YELLOW"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ai/metrics — ROLE=ADMIN 없으면 403")
    void adminMetrics_forbidden_withoutAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/ai/metrics — ROLE=ADMIN 이면 200")
    void adminMetrics_ok_withAdmin() throws Exception {
        when(aiModelMetricService.findMetrics(any(), anyInt()))
                .thenReturn(List.of(new AiMetricDto(
                        1L, "risk-model", "RISK_SCORE", "DAILY",
                        java.time.LocalDate.of(2026, 5, 17),
                        null, null, null, 100, false)));

        mockMvc.perform(get("/api/v1/admin/ai/metrics")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modelName").value("risk-model"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ai/drift-alerts — ROLE=ADMIN 없으면 403")
    void adminDriftAlerts_forbidden_withoutAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/drift-alerts")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/ai/drift-alerts — ROLE=ADMIN 이면 200")
    void adminDriftAlerts_ok_withAdmin() throws Exception {
        when(aiModelMetricService.findDriftAlerts()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/ai/drift-alerts")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk());
    }
}
