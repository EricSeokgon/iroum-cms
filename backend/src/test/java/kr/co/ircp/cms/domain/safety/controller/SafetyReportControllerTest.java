package kr.co.ircp.cms.domain.safety.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.CheckResultRequest;
import kr.co.ircp.cms.domain.safety.dto.CheckResultResponse;
import kr.co.ircp.cms.domain.safety.dto.ChecklistStatsResponse;
import kr.co.ircp.cms.domain.safety.dto.ReportCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.ReportDetail;
import kr.co.ircp.cms.domain.safety.dto.ReportSummary;
import kr.co.ircp.cms.domain.safety.exception.SafetyReportNotFoundException;
import kr.co.ircp.cms.domain.safety.service.SafetyChecklistService;
import kr.co.ircp.cms.domain.safety.service.SafetyGuidelineService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SafetyReportController @WebMvcTest (GREEN 단계).
 *
 * <p>REQ-SAFETY-003 + REQ-SAFETY-004 — 가이드라인 보고서 + 체크리스트 HTTP 계층 검증.
 * {@code @AuthenticationPrincipal Long companyId} 주입 동작 확인 포함.
 */
@WebMvcTest(SafetyReportController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SafetyReportController GREEN 테스트 (REQ-SAFETY-003+004)")
class SafetyReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SafetyGuidelineService guidelineService;

    @MockitoBean
    private SafetyChecklistService checklistService;

    private static final Long COMPANY_ID = 100L;

    private static ReportDetail sampleReportDetail(UUID uuid) {
        return new ReportDetail(
                1L, uuid, COMPANY_ID, 10L, "HIGH",
                "[]", "<html>보고서</html>", "/pdf/report.pdf",
                Instant.parse("2026-04-01T00:00:00Z"), 0
        );
    }

    @Test
    @DisplayName("POST /api/v1/safety/reports — 보고서 생성 201 Created")
    void generate_returnsCreated() throws Exception {
        UUID uuid = UUID.randomUUID();
        ReportDetail created = sampleReportDetail(uuid);
        when(guidelineService.generateReport(eq(COMPANY_ID), any())).thenReturn(created);

        ReportCreateRequest request = new ReportCreateRequest(10L);

        mockMvc.perform(post("/api/v1/safety/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.uuid").value(uuid.toString()))
                .andExpect(jsonPath("$.riskGrade").value("HIGH"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/reports/{uuid} — 보고서 조회 200 OK")
    void getReport_returnsOk() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(guidelineService.getReport(eq(uuid), anyBoolean(), eq(COMPANY_ID)))
                .thenReturn(sampleReportDetail(uuid));

        mockMvc.perform(get("/api/v1/safety/reports/" + uuid)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(uuid.toString()))
                .andExpect(jsonPath("$.contentHtml").value("<html>보고서</html>"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/reports/{uuid} — 미존재 시 404 Not Found")
    void getReport_notFound_returns404() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(guidelineService.getReport(eq(uuid), anyBoolean(), eq(COMPANY_ID)))
                .thenThrow(new SafetyReportNotFoundException(uuid));

        mockMvc.perform(get("/api/v1/safety/reports/" + uuid)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/safety/reports/{uuid}/pdf — PDF 경로 200 OK")
    void getPdfPath_returnsOk() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(guidelineService.getReportPdfPath(eq(uuid), anyBoolean(), eq(COMPANY_ID)))
                .thenReturn("/files/report-" + uuid + ".pdf");

        mockMvc.perform(get("/api/v1/safety/reports/" + uuid + "/pdf")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/safety/reports/me — 내 보고서 목록 200 OK")
    void myReports_returnsOk() throws Exception {
        ReportSummary item = new ReportSummary(
                1L, UUID.randomUUID(), COMPANY_ID, 10L, "HIGH",
                Instant.now(), 0
        );
        PageResponse<ReportSummary> page = PageResponse.of(List.of(item), 0, 20, 1L);
        when(guidelineService.listMyReports(eq(COMPANY_ID), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/safety/reports/me")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/reports — 전체 보고서 목록 200 OK")
    void allReports_returnsOk() throws Exception {
        PageResponse<ReportSummary> page = PageResponse.of(
                List.of(new ReportSummary(2L, UUID.randomUUID(), 200L, 11L, "MEDIUM", Instant.now(), 0)),
                0, 20, 1L
        );
        when(guidelineService.listAllReports(anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/safety/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/safety/reports/{uuid}/checklist — 체크리스트 200 OK")
    void checklist_returnsOk() throws Exception {
        UUID uuid = UUID.randomUUID();
        CheckResultResponse item = new CheckResultResponse(
                1L, "PPE", "안전모 착용", "HIGH",
                "DONE", "사진 첨부 완료", null, COMPANY_ID, Instant.now()
        );
        when(checklistService.getChecklistByReport(eq(uuid), anyBoolean(), eq(COMPANY_ID)))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/safety/reports/" + uuid + "/checklist")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].itemId").value(1))
                .andExpect(jsonPath("$[0].status").value("DONE"));
    }

    @Test
    @DisplayName("PUT /api/v1/safety/reports/{uuid}/checklist/{itemId} — 체크 결과 200 OK")
    void upsertCheckResult_returnsOk() throws Exception {
        UUID uuid = UUID.randomUUID();
        CheckResultRequest request = new CheckResultRequest("DONE", "확인 완료", null);
        CheckResultResponse response = new CheckResultResponse(
                1L, "PPE", "안전모 착용", "HIGH",
                "DONE", "확인 완료", null, COMPANY_ID, Instant.now()
        );
        when(checklistService.upsertCheckResult(
                eq(uuid), eq(1L), any(CheckResultRequest.class),
                eq(COMPANY_ID), anyBoolean(), eq(COMPANY_ID)
        )).thenReturn(response);

        mockMvc.perform(put("/api/v1/safety/reports/" + uuid + "/checklist/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    @DisplayName("PUT /api/v1/safety/reports/{uuid}/checklist/{itemId} — 필수 필드 누락 시 400")
    void upsertCheckResult_missingStatus_returns400() throws Exception {
        UUID uuid = UUID.randomUUID();
        // status (@NotBlank) 누락
        String invalidJson = "{\"evidenceText\":\"text\"}";

        mockMvc.perform(put("/api/v1/safety/reports/" + uuid + "/checklist/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(companyAuth(COMPANY_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/checklist/stats — 통계 200 OK")
    void stats_returnsOk() throws Exception {
        ChecklistStatsResponse stats = new ChecklistStatsResponse(
                10L, 100L, 60L, 20L, 5L, 15L, 0.6
        );
        when(checklistService.getOverallStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/safety/admin/checklist/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReports").value(10))
                .andExpect(jsonPath("$.doneCount").value(60))
                .andExpect(jsonPath("$.completionRate").value(0.6));
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // SafetyReportController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(.anyRequest().authenticated())로
    // /api/v1/safety/** 경로 인증만 강제된다. 권한(role/authority)별 차등 통제는 컨트롤러
    // 내부 isAdmin() 헬퍼(SecurityContextHolder 기반)로 service 단계에서 수행된다.
    //
    // 또한 대부분의 endpoint는 @AuthenticationPrincipal Long companyId 인자를 사용하므로
    // 익명 요청 시 SecurityContext가 비어 companyId가 null로 주입되어
    // controller/service 본체에서 NullPointerException(500)이 발생한다 — 401/403 결정 검증 불가.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────

    // ─── 헬퍼: principal로 JwtPrincipal 사용 (SPEC-CMS-SECURITY-IDOR) ───────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken companyAuth(
            Long companyId) {
        kr.co.ircp.cms.domain.auth.security.JwtPrincipal principal =
                new kr.co.ircp.cms.domain.auth.security.JwtPrincipal(
                        companyId, "user-" + companyId, java.util.Set.of("USER"));
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
