package kr.co.ircp.cms.domain.policy.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleCreateRequest;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleResponse;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleConflictException;
import kr.co.ircp.cms.domain.policy.dispatch.exception.DispatchScheduleNotFoundException;
import kr.co.ircp.cms.domain.policy.dispatch.service.PolicyDispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PolicyDispatchController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-007 REQ-POLICY-003: 정책 알림 발송 예약 + 즉시 트리거 + 취소 HTTP 계층 검증.
 */
@WebMvcTest(PolicyDispatchController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PolicyDispatchController GREEN 테스트 (REQ-POLICY-003)")
class PolicyDispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PolicyDispatchService dispatchService;

    private static DispatchScheduleResponse sampleSchedule(Long id, String status) {
        return new DispatchScheduleResponse(
                id, UUID.randomUUID(), 100L,
                "POLICY_BROADCAST", "{\"industry\":\"IT\"}",
                Instant.parse("2026-06-15T09:00:00Z"),
                false, null,
                List.of("EMAIL", "PUSH"), 555L, 5,
                status, 1L, Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"POLICY:DISPATCH:READ"})
    @DisplayName("GET /policy/admin/dispatch/schedules — 발송 예약 목록 200 OK")
    void listSchedules_returnsOkWithPage() throws Exception {
        PageResponse<DispatchScheduleResponse> page = PageResponse.of(
                List.of(sampleSchedule(1L, "PENDING"), sampleSchedule(2L, "PROCESSING")),
                0, 20, 2L
        );
        when(dispatchService.listSchedules(eq("PENDING"), eq(100L), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/policy/admin/dispatch/schedules")
                        .param("status", "PENDING")
                        .param("policyId", "100")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/dispatch/schedules — 발송 예약 생성 201 Created")
    void createSchedule_returnsCreated() throws Exception {
        DispatchScheduleCreateRequest req = new DispatchScheduleCreateRequest(
                100L, "POLICY_BROADCAST", "{\"industry\":\"IT\"}",
                Instant.parse("2026-06-15T09:00:00Z"),
                List.of("EMAIL"), 555L, 5, 1L
        );
        when(dispatchService.createSchedule(any(DispatchScheduleCreateRequest.class)))
                .thenReturn(sampleSchedule(77L, "PENDING"));

        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/dispatch/schedules — 필수 필드(scheduledAt) 누락 시 400 Bad Request")
    void createSchedule_missingScheduledAt_returns400() throws Exception {
        // scheduledAt(@NotNull) 누락
        String invalidJson = "{\"policyId\":100,\"dispatchType\":\"POLICY_BROADCAST\","
                + "\"channels\":[\"EMAIL\"],\"templateId\":555,\"createdBy\":1}";

        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/dispatch/schedules/{id}/trigger — 즉시 트리거 200 OK")
    void triggerNow_returnsOk() throws Exception {
        when(dispatchService.triggerNow(eq(77L)))
                .thenReturn(sampleSchedule(77L, "PROCESSING"));

        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules/77/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/dispatch/schedules/{id}/trigger — 미존재 시 404 Not Found")
    void triggerNow_notFound_returns404() throws Exception {
        when(dispatchService.triggerNow(eq(999L)))
                .thenThrow(new DispatchScheduleNotFoundException(999L));

        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules/999/trigger"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/dispatch/schedules/{id}/cancel — 예약 취소 204 No Content")
    void cancelSchedule_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules/77/cancel"))
                .andExpect(status().isNoContent());

        verify(dispatchService).cancelSchedule(77L);
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN"})
    @DisplayName("POST /policy/admin/dispatch/schedules/{id}/cancel — PROCESSING 이후 취소 시 409 Conflict")
    void cancelSchedule_conflict_returns409() throws Exception {
        doThrow(new DispatchScheduleConflictException("이미 처리 중인 예약은 취소할 수 없습니다."))
                .when(dispatchService).cancelSchedule(eq(99L));

        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules/99/cancel"))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // PolicyDispatchController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(.anyRequest().authenticated())로
    // /api/v1/policy/admin/dispatch/** 경로 인증만 강제된다. 권한(role/authority)별 차등 통제는 없다.
    //
    // 본 슬라이스 테스트는 SecurityAutoConfiguration을 제외하므로 HTTP 레벨 정책이 미적용되며,
    // 메소드 레벨 정책 거부 트리거가 없어 ExceptionTranslationFilter가 EntryPoint를 호출하지 않는다.
    // 따라서 슬라이스에서 401(미인증) / 403(권한 부족) 응답을 결정적으로 검증할 수 없다.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────
}
