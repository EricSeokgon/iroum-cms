package kr.co.ircp.cms.domain.approval.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.approval.dto.BulkOperationResult;
import kr.co.ircp.cms.domain.approval.dto.UserApprovalSummary;
import kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException;
import kr.co.ircp.cms.domain.approval.service.UserApprovalService;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-USER-APPROVAL-001 — UserApprovalController @WebMvcTest.
 *
 * <p>6개 엔드포인트의 happy path + 권한(403)/검증(400)/충돌(409) 검증.
 * 클래스 레벨 {@code @PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")} 동작 포함.
 */
@WebMvcTest(UserApprovalController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("UserApprovalController GREEN 테스트 (SPEC-CMS-USER-APPROVAL-001)")
class UserApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserApprovalService approvalService;

    private static final JwtPrincipal SUPER_ADMIN =
            new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of());
    private static final JwtPrincipal DEPT_ADMIN =
            new JwtPrincipal(2L, "deptadmin", Set.of("DEPT_ADMIN"), Set.of());
    private static final JwtPrincipal NORMAL_USER =
            new JwtPrincipal(3L, "user", Set.of("USER"), Set.of());

    // ─── 목록 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /approvals — SUPER_ADMIN: 200 + 페이지 응답")
    void list_superAdmin_returnsOk() throws Exception {
        UserApprovalSummary s = new UserApprovalSummary(
                10L, "u10@example.com", "u10@example.com", "홍길동", Instant.now(), null, null);
        when(approvalService.getPendingApprovals(anyInt(), anyInt(), any()))
                .thenReturn(new UserApprovalService.PageResult(List.of(s), 1));

        mockMvc.perform(get("/api/v1/users/approvals")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(10))
                .andExpect(jsonPath("$.content[0].name").value("홍길동"));
    }

    @Test
    @DisplayName("GET /approvals — DEPT_ADMIN 도 접근 가능: 200")
    void list_deptAdmin_returnsOk() throws Exception {
        when(approvalService.getPendingApprovals(anyInt(), anyInt(), any()))
                .thenReturn(new UserApprovalService.PageResult(List.of(), 0));

        mockMvc.perform(get("/api/v1/users/approvals")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(DEPT_ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /approvals — 권한 없는 USER: 403 (REQ-UA-020)")
    void list_normalUser_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/approvals")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(NORMAL_USER))))
                .andExpect(status().isForbidden());
    }

    // ─── 단건 승인/거절 ────────────────────────────────────────────

    @Test
    @DisplayName("POST /approvals/{id}/approve — 200 + 서비스 호출(처리자=principal)")
    void approve_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/users/approvals/10/approve")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isOk());
        verify(approvalService).approve(eq(10L), eq(1L));
    }

    @Test
    @DisplayName("POST /approvals/{id}/approve — 대기 상태 아님: 409 (REQ-UA-013)")
    void approve_notPending_conflict() throws Exception {
        doThrow(new UserNotPendingApprovalException(10L))
                .when(approvalService).approve(eq(10L), anyLong());

        mockMvc.perform(post("/api/v1/users/approvals/10/approve")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /approvals/{id}/reject — 사유 포함: 200 + 서비스 호출")
    void reject_withReason_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/users/approvals/20/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"부적격\"}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isOk());
        verify(approvalService).reject(eq(20L), eq("부적격"), eq(1L));
    }

    @Test
    @DisplayName("POST /approvals/{id}/reject — 사유 누락: 400 (REQ-UA-012)")
    void reject_blankReason_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users/approvals/20/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /approvals/{id}/reject — 권한 없는 USER: 403")
    void reject_normalUser_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/users/approvals/20/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"부적격\"}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(NORMAL_USER))))
                .andExpect(status().isForbidden());
    }

    // ─── 일괄 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /approvals/bulk-approve — 200 + 집계 결과")
    void bulkApprove_returnsResult() throws Exception {
        when(approvalService.bulkApprove(any(), eq(1L)))
                .thenReturn(new BulkOperationResult(2, 0, List.of()));

        mockMvc.perform(post("/api/v1/users/approvals/bulk-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[30,31]}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(0));
    }

    @Test
    @DisplayName("POST /approvals/bulk-reject — 200 + 공통 사유 전달")
    void bulkReject_returnsResult() throws Exception {
        when(approvalService.bulkReject(any(), eq("일괄 사유"), eq(1L)))
                .thenReturn(new BulkOperationResult(1, 1,
                        List.of(new BulkOperationResult.Failure(41L, "승인 대기 상태가 아닙니다."))));

        mockMvc.perform(post("/api/v1/users/approvals/bulk-reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[40,41],\"reason\":\"일괄 사유\"}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failures[0].userId").value(41));
    }

    @Test
    @DisplayName("POST /approvals/bulk-reject — 사유 누락: 400")
    void bulkReject_blankReason_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users/approvals/bulk-reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[40],\"reason\":\"\"}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(SUPER_ADMIN))))
                .andExpect(status().isBadRequest());
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken jwtAuth(
            JwtPrincipal principal) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                principal.roles().stream()
                        .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                        .toList());
    }
}
