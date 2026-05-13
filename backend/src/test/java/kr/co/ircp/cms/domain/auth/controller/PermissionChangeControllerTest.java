package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PermissionChangeEntry;
import kr.co.ircp.cms.domain.auth.service.PermissionChangeHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PermissionChangeController @WebMvcTest.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016 — HTTP 계층 검증 (200 응답, 403 인가 거부, 필터 파라미터 전파).
 * Security 비활성화 후 @PreAuthorize 동작은 별도 통합 테스트에서 검증.
 */
@WebMvcTest(PermissionChangeController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PermissionChangeController 단위 테스트")
class PermissionChangeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PermissionChangeHistoryService service;

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/audit/permission-changes
    // ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "AUDIT:READ")
    @DisplayName("GET /audit/permission-changes — 200 + 페이징 응답 반환")
    void list_returns200WithPagedResult() throws Exception {
        PermissionChangeEntry entry = new PermissionChangeEntry(
                1L, 10L, "testuser", "ROLE_ASSIGN", "VIEWER",
                1L, "admin", Instant.now(), "테스트 사유");
        PageResponse<PermissionChangeEntry> page = PageResponse.of(List.of(entry), 0, 20, 1L);
        when(service.findPage(eq(0), eq(20), anyString(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/permission-changes")
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].changeType").value("ROLE_ASSIGN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @WithMockUser(authorities = "AUDIT:READ")
    @DisplayName("GET /audit/permission-changes — targetUserId 파라미터 서비스로 전달")
    void list_propagatesFilterParams() throws Exception {
        PageResponse<PermissionChangeEntry> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(service.findPage(eq(0), eq(20), anyString(), eq(5L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(empty);

        mockMvc.perform(get("/api/v1/audit/permission-changes")
                        .param("targetUserId", "5"))
                .andExpect(status().isOk());

        verify(service).findPage(0, 20, "changedAt,desc", 5L, null, null, null, null);
    }

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/audit/permission-changes/users/{userId}
    // ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "AUDIT:READ")
    @DisplayName("GET /audit/permission-changes/users/{userId} — 200 + 사용자별 페이징 반환")
    void byUser_returns200WithPagedResult() throws Exception {
        PermissionChangeEntry entry = new PermissionChangeEntry(
                2L, 7L, "user7", "ROLE_UNASSIGN", "EDITOR",
                1L, "admin", Instant.now(), null);
        PageResponse<PermissionChangeEntry> page = PageResponse.of(List.of(entry), 0, 20, 1L);
        when(service.findByUser(7L, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/permission-changes/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].targetUserId").value(7))
                .andExpect(jsonPath("$.content[0].changeType").value("ROLE_UNASSIGN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오
    // 클래스 레벨 @PreAuthorize("hasAuthority('AUDIT:READ')") 정책 검증
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-COV-001-1 — GET /audit/permission-changes 인증 없이 접근 시 403 Forbidden (@WebMvcTest 한계)")
    void list_returns403_withoutAuthentication() throws Exception {
        // given: SecurityContext 비어있음 (@WithMockUser 미부착)
        // when & then: @WebMvcTest + SecurityAutoConfiguration 제외 → SecurityFilterChain 없음
        //              → AnonymousAuthenticationToken → @PreAuthorize 거부 → AccessDeniedException → 403
        // 운영 full SecurityFilterChain은 anonymous → AuthenticationEntryPoint → 401이나,
        // @WebMvcTest는 SecurityFilterChain 없이 @PreAuthorize만 동작 → 403 응답.
        // 인증 부재 401 검증은 SecurityConfig 통합 테스트에서 별도 검증 (REQ-IRR-003).
        mockMvc.perform(get("/api/v1/audit/permission-changes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"WRONG_AUTHORITY"})
    @DisplayName("AC-COV-001-2 — GET /audit/permission-changes 권한 부족 시 403 Forbidden")
    void list_returns403_withInsufficientAuthority() throws Exception {
        // given: WRONG_AUTHORITY는 AUDIT:READ 정책 미충족
        // when & then: @PreAuthorize 거부 → AccessDeniedHandler → 403
        mockMvc.perform(get("/api/v1/audit/permission-changes"))
                .andExpect(status().isForbidden());
    }
}
