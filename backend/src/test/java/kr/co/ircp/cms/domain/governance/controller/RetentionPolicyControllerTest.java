package kr.co.ircp.cms.domain.governance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.governance.batch.AccessLogRetentionJob;
import kr.co.ircp.cms.domain.governance.batch.AuditLogArchiveJob;
import kr.co.ircp.cms.domain.governance.batch.IntegrationLogRetentionJob;
import kr.co.ircp.cms.domain.governance.batch.LoginHistoryPurgeJob;
import kr.co.ircp.cms.domain.governance.batch.PersonalDataRetentionJob;
import kr.co.ircp.cms.domain.governance.dto.RetentionPolicyRequest;
import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.service.RetentionPolicyService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RetentionPolicyController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-006~009: 보존 정책 CRUD + 수동 실행 HTTP 계층 검증.
 */
@WebMvcTest(RetentionPolicyController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("RetentionPolicyController GREEN 테스트 (REQ-GOV-006~009)")
class RetentionPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RetentionPolicyService service;

    @MockBean
    private PersonalDataRetentionJob personalJob;

    @MockBean
    private AuditLogArchiveJob auditJob;

    @MockBean
    private LoginHistoryPurgeJob loginJob;

    @MockBean
    private AccessLogRetentionJob accessJob;

    @MockBean
    private IntegrationLogRetentionJob integrationJob;

    private static RetentionPolicy samplePolicy(Long id, String targetTable) {
        return RetentionPolicy.builder()
                .id(id)
                .targetTable(targetTable)
                .policyType("DELETE")
                .retentionMonths(36)
                .scheduleCron("0 0 3 * * *")
                .status("ACTIVE")
                .description("36개월 보존 후 삭제")
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /retention-policies — 목록 조회 200 OK + 응답 배열")
    void list_returnsOkWithPolicies() throws Exception {
        // given
        when(service.findAll()).thenReturn(List.of(
                samplePolicy(1L, "personal_data_access_log"),
                samplePolicy(2L, "audit_log")
        ));

        // when & then
        mockMvc.perform(get("/api/v1/governance/retention-policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].targetTable").value("personal_data_access_log"))
                .andExpect(jsonPath("$[1].targetTable").value("audit_log"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /retention-policies — 생성 201 Created + Location 헤더 + 응답 ID")
    void create_returnsCreatedWithBody() throws Exception {
        // given
        RetentionPolicyRequest req = new RetentionPolicyRequest(
                "audit_log", "ARCHIVE", 36, "audit_log_archive",
                null, "0 0 4 * * *", "ACTIVE", "감사로그 36개월 후 ARCHIVE");
        RetentionPolicy created = samplePolicy(10L, "audit_log");
        when(service.create(any(RetentionPolicy.class))).thenReturn(created);

        // when & then
        mockMvc.perform(post("/api/v1/governance/retention-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.targetTable").value("audit_log"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /retention-policies — targetTable 누락 시 400 Bad Request")
    void create_invalidRequest_returns400() throws Exception {
        // given — targetTable, policyType, retentionMonths 모두 누락
        String invalidJson = "{}";

        // when & then
        mockMvc.perform(post("/api/v1/governance/retention-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /retention-policies/{id} — 수정 200 OK + 응답 body")
    void update_returnsOkWithUpdatedPolicy() throws Exception {
        // given
        RetentionPolicyRequest req = new RetentionPolicyRequest(
                "audit_log", "ARCHIVE", 60, "audit_log_archive",
                null, "0 0 4 * * *", "ACTIVE", "감사로그 60개월 보존으로 변경");
        RetentionPolicy updated = RetentionPolicy.builder()
                .id(5L)
                .targetTable("audit_log")
                .policyType("ARCHIVE")
                .retentionMonths(60)
                .status("ACTIVE")
                .build();
        when(service.update(any(RetentionPolicy.class))).thenReturn(updated);

        // when & then
        mockMvc.perform(put("/api/v1/governance/retention-policies/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.retentionMonths").value(60));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /retention-policies/{id}/run — personal_data_access_log 즉시 실행 200 OK + processed 반환")
    void run_personalData_returnsOkWithProcessed() throws Exception {
        // given
        RetentionPolicy policy = samplePolicy(7L, "personal_data_access_log");
        when(service.findById(eq(7L))).thenReturn(Optional.of(policy));
        when(personalJob.run(anyBoolean())).thenReturn(42);

        // when & then
        mockMvc.perform(post("/api/v1/governance/retention-policies/7/run")
                        .param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.targetTable").value("personal_data_access_log"))
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.processed").value(42));

        verify(personalJob).run(false);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /retention-policies/{id}/run — dryRun=true는 personal_data_access_log 외엔 0 반환")
    void run_dryRun_returnsZeroForNonPersonal() throws Exception {
        // given — audit_log 정책에 dryRun=true → 실제 Job 실행 없이 0 반환
        RetentionPolicy policy = samplePolicy(8L, "audit_log");
        when(service.findById(eq(8L))).thenReturn(Optional.of(policy));

        // when & then
        mockMvc.perform(post("/api/v1/governance/retention-policies/8/run")
                        .param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTable").value("audit_log"))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.processed").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /retention-policies/{id}/run — audit_log 정책 실제 실행 200 OK")
    void run_auditLog_invokesAuditJob() throws Exception {
        // given
        RetentionPolicy policy = samplePolicy(20L, "audit_log");
        when(service.findById(eq(20L))).thenReturn(Optional.of(policy));
        when(auditJob.run()).thenReturn(123);

        // when & then
        mockMvc.perform(post("/api/v1/governance/retention-policies/20/run")
                        .param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(123));

        verify(auditJob).run();
    }
}
