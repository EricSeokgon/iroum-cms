package kr.co.ircp.cms.domain.policy.program.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramCreateRequest;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramDetail;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSummary;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramSyncResult;
import kr.co.ircp.cms.domain.policy.program.dto.PolicyProgramUpdateRequest;
import kr.co.ircp.cms.domain.policy.program.exception.PolicyProgramNotFoundException;
import kr.co.ircp.cms.domain.policy.program.service.PolicyProgramService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PolicyProgramController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-007 REQ-POLICY-001: 정책사업 마스터 CRUD + K-Startup 동기화 HTTP 계층 검증.
 */
@WebMvcTest(PolicyProgramController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PolicyProgramController GREEN 테스트 (REQ-POLICY-001)")
class PolicyProgramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicyProgramService programService;

    private static PolicyProgramSummary sampleSummary(Long id, String code) {
        return new PolicyProgramSummary(
                id, code, "중기부", "청년창업지원사업",
                List.of("IT", "바이오"), List.of("서울", "경기"),
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T23:59:59Z"),
                "ACTIVE"
        );
    }

    private static PolicyProgramDetail sampleDetail(Long id, String code) {
        return new PolicyProgramDetail(
                id, code, "중기부", "청년창업지원사업",
                "Youth Startup Program", "<p>설명</p>",
                List.of("IT"), List.of("서울"),
                1, 50, 0L, 5_000_000_000L,
                0, 36,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T23:59:59Z"),
                10_000_000_000L, 100_000_000L,
                "https://k-startup.go.kr/p/1", "ACTIVE",
                Instant.now(), Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"POLICY:PROGRAM:READ"})
    @DisplayName("GET /policy/programs — 정책사업 목록 200 OK")
    void listPrograms_returnsOkWithPage() throws Exception {
        PageResponse<PolicyProgramSummary> page = PageResponse.of(
                List.of(sampleSummary(1L, "P1"), sampleSummary(2L, "P2")),
                0, 20, 2L
        );
        when(programService.listPrograms(eq("ACTIVE"), eq("IT"), eq("서울"), eq("청년"), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/policy/programs")
                        .param("status", "ACTIVE")
                        .param("industry", "IT")
                        .param("region", "서울")
                        .param("keyword", "청년")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].code").value("P1"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:PROGRAM:READ"})
    @DisplayName("GET /policy/programs/{id} — 정책사업 단건 조회 200 OK")
    void getProgram_returnsOkWithDetail() throws Exception {
        when(programService.getProgram(eq(7L))).thenReturn(sampleDetail(7L, "P_7"));

        mockMvc.perform(get("/api/v1/policy/programs/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.code").value("P_7"))
                .andExpect(jsonPath("$.programName").value("청년창업지원사업"));
    }

    @Test
    @WithMockUser(authorities = {"POLICY:PROGRAM:READ"})
    @DisplayName("GET /policy/programs/{id} — 미존재 시 404 Not Found")
    void getProgram_notFound_returns404() throws Exception {
        when(programService.getProgram(eq(999L)))
                .thenThrow(new PolicyProgramNotFoundException(999L));

        mockMvc.perform(get("/api/v1/policy/programs/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/programs — 정책사업 생성 201 Created")
    void createProgram_returnsCreated() throws Exception {
        PolicyProgramCreateRequest req = new PolicyProgramCreateRequest(
                "NEW_P", "중기부", "신규 정책",
                null, null, List.of("IT"), List.of("서울"),
                null, null, null, null, null, null,
                null, null, null, null, null, "ACTIVE"
        );
        when(programService.createProgram(any(PolicyProgramCreateRequest.class)))
                .thenReturn(sampleDetail(50L, "NEW_P"));

        mockMvc.perform(post("/api/v1/policy/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.code").value("NEW_P"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/programs — 필수 필드(code) 누락 시 400 Bad Request")
    void createProgram_missingCode_returns400() throws Exception {
        // code(@NotBlank) 누락
        String invalidJson = "{\"ministry\":\"중기부\",\"programName\":\"이름\"}";

        mockMvc.perform(post("/api/v1/policy/admin/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("PUT /policy/admin/programs/{id} — 정책사업 수정 200 OK")
    void updateProgram_returnsOk() throws Exception {
        PolicyProgramUpdateRequest req = new PolicyProgramUpdateRequest(
                "수정된 이름", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, "ACTIVE"
        );
        when(programService.updateProgram(eq(5L), any(PolicyProgramUpdateRequest.class)))
                .thenReturn(sampleDetail(5L, "P_5"));

        mockMvc.perform(put("/api/v1/policy/admin/programs/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("DELETE /policy/admin/programs/{id} — 정책사업 삭제 204 No Content")
    void deleteProgram_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/policy/admin/programs/3"))
                .andExpect(status().isNoContent());

        verify(programService).deleteProgram(3L);
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/programs/sync — K-Startup 외부 동기화 200 OK")
    void syncFromExternal_returnsOk() throws Exception {
        PolicyProgramSyncResult result = new PolicyProgramSyncResult(
                "K_STARTUP", 100, 30, 50, 20, Instant.now()
        );
        when(programService.syncFromExternal(eq("K_STARTUP"))).thenReturn(result);

        mockMvc.perform(post("/api/v1/policy/admin/programs/sync")
                        .param("sourceCode", "K_STARTUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("K_STARTUP"))
                .andExpect(jsonPath("$.fetched").value(100))
                .andExpect(jsonPath("$.inserted").value(30));
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // PolicyProgramController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(.anyRequest().authenticated())로
    // /api/v1/policy/** 경로 인증만 강제된다. 권한(role/authority)별 차등 통제는 없다.
    //
    // 본 슬라이스 테스트는 SecurityAutoConfiguration을 제외하므로 HTTP 레벨 정책이 미적용되며,
    // 메소드 레벨 정책 거부 트리거가 없어 ExceptionTranslationFilter가 EntryPoint를 호출하지 않는다.
    // 따라서 슬라이스에서 401(미인증) / 403(권한 부족) 응답을 결정적으로 검증할 수 없다.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────
}
