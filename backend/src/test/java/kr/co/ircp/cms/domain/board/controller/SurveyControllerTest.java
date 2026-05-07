package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.SurveyAnswerRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyCreateRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyDetail;
import kr.co.ircp.cms.domain.board.dto.SurveyQuestionRequest;
import kr.co.ircp.cms.domain.board.dto.SurveyResultDto;
import kr.co.ircp.cms.domain.board.dto.SurveySubmitRequest;
import kr.co.ircp.cms.domain.board.dto.SurveySummary;
import kr.co.ircp.cms.domain.board.exception.SurveyNotFoundException;
import kr.co.ircp.cms.domain.board.exception.SurveyPeriodInvalidException;
import kr.co.ircp.cms.domain.board.service.SurveyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SurveyController GREEN 단계 테스트.
 * REQ-BOARD-013: 설문조사 CRUD + 응답 제출 + 결과 통계 HTTP 계층 검증.
 */
@WebMvcTest(SurveyController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SurveyController GREEN 테스트 (REQ-BOARD-013)")
class SurveyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SurveyService surveyService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal EDITOR_PRINCIPAL =
            new JwtPrincipal(99L, "editor", Set.of("EDITOR"));

    /**
     * JwtPrincipal 기반 인증 토큰 헬퍼.
     * JwtPrincipal.getAuthorities() 가 ROLE_ prefix 자동 부여 → 그대로 사용.
     */
    private UsernamePasswordAuthenticationToken jwtAuth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.getAuthorities().stream()
                        .map(a -> (GrantedAuthority) a)
                        .toList());
    }

    private SurveyDetail sampleDetail(Long id) {
        return new SurveyDetail(
                id, "고객 만족도 조사", "<p>설명</p>", "ACTIVE",
                false, 1000, 0,
                Instant.now(), Instant.now().plusSeconds(86400),
                Instant.now(), List.of()
        );
    }

    @Test
    @DisplayName("GET /api/v1/surveys — 200 OK, 페이징 응답 반환 (공개)")
    void list_returns200WithPage() throws Exception {
        // given
        SurveySummary summary = new SurveySummary(
                1L, "만족도 조사", "ACTIVE", false, 1000, 5,
                Instant.now(), Instant.now().plusSeconds(86400), Instant.now()
        );
        PageResponse<SurveySummary> page = PageResponse.of(List.of(summary), 0, 20, 1L);
        when(surveyService.listSurveys(any(), any(), anyInt(), anyInt())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/surveys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("만족도 조사"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/surveys/{id} — 200 OK, 단건 상세 반환 (공개)")
    void getDetail_existing_returns200() throws Exception {
        // given
        when(surveyService.getSurvey(1L)).thenReturn(sampleDetail(1L));

        // when & then
        mockMvc.perform(get("/api/v1/surveys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/surveys/{id} — 미존재 시 404 + SURVEY_NOT_FOUND")
    void getDetail_nonExistent_returns404() throws Exception {
        // given
        when(surveyService.getSurvey(999L)).thenThrow(new SurveyNotFoundException(999L));

        // when & then
        mockMvc.perform(get("/api/v1/surveys/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SURVEY_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/v1/surveys — ADMIN 인증 시 201 Created + 등록된 설문 반환")
    void create_validRequest_returns201_whenAdmin() throws Exception {
        // given
        when(surveyService.createSurvey(any(), any())).thenReturn(sampleDetail(7L));
        SurveyCreateRequest req = new SurveyCreateRequest(
                "만족도 조사", "<p>설명</p>", "설명",
                Instant.now(), Instant.now().plusSeconds(86400),
                false, 1000,
                List.of(new SurveyQuestionRequest("Q1", "TEXT", true, 1, null))
        );

        // when & then
        mockMvc.perform(post("/api/v1/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("POST /api/v1/surveys/{id}/responses — 익명 응답 시 204 No Content (공개)")
    void submitResponse_anonymous_returns204() throws Exception {
        // given
        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(10L, "응답", null, null, null))
        );

        // when & then — 익명 사용자 허용
        mockMvc.perform(post("/api/v1/surveys/1/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/surveys/{id}/responses — 설문 기간 외 시 400 + SURVEY_PERIOD_INVALID")
    void submitResponse_periodInvalid_returns400() throws Exception {
        // given
        doThrow(new SurveyPeriodInvalidException())
                .when(surveyService).submitResponse(eq(1L), any(), any(), anyString());
        SurveySubmitRequest req = new SurveySubmitRequest(
                List.of(new SurveyAnswerRequest(10L, "응답", null, null, null))
        );

        // when & then
        mockMvc.perform(post("/api/v1/surveys/1/responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SURVEY_PERIOD_INVALID"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/surveys/{id}/results — ADMIN 인증 시 200 OK + 결과 통계 반환")
    void getResults_returns200_whenAdmin() throws Exception {
        // given
        SurveyResultDto result = new SurveyResultDto(
                1L, "만족도 조사", 100, List.of()
        );
        when(surveyService.getResults(1L)).thenReturn(result);

        // when & then
        mockMvc.perform(get("/api/v1/surveys/1/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(1))
                .andExpect(jsonPath("$.totalResponses").value(100));
    }

    // ─── 인증/인가 거부 시나리오 (REQ-BOARD-013 보안 가드) ─────────────────────

    @Test
    @DisplayName("POST /api/v1/surveys — 미인증 시 403 Forbidden (익명 인증 토큰 → AccessDeniedException)")
    void create_rejectsUnauthenticated() throws Exception {
        // AnonymousAuthenticationFilter 가 익명 Authentication 을 부여하므로
        // @PreAuthorize 거부 시 AccessDeniedException → HTTP 403 으로 응답된다.
        SurveyCreateRequest req = new SurveyCreateRequest(
                "익명 시도", "<p>설명</p>", "설명",
                Instant.now(), Instant.now().plusSeconds(86400),
                false, 1000,
                List.of(new SurveyQuestionRequest("Q1", "TEXT", true, 1, null))
        );
        String body = objectMapper.writeValueAsString(req);
        mockMvc.perform(post("/api/v1/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/surveys — EDITOR 역할 시 403 (CONTENT:WRITE/ADMIN 권한 없음)")
    void create_returns403_whenEditorRole() throws Exception {
        SurveyCreateRequest req = new SurveyCreateRequest(
                "권한 없는 시도", "<p>설명</p>", "설명",
                Instant.now(), Instant.now().plusSeconds(86400),
                false, 1000,
                List.of(new SurveyQuestionRequest("Q1", "TEXT", true, 1, null))
        );
        mockMvc.perform(post("/api/v1/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(EDITOR_PRINCIPAL))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
