package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.LoginHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.LoginHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import kr.co.ircp.cms.support.WebMvcTestInfraConfig;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LoginHistoryController @WebMvcTest.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — HTTP 계층 검증 (200 응답, 403 인가 거부, 필터 파라미터 전파, 날짜 파싱).
 */
@WebMvcTest(LoginHistoryController.class)
@Import(WebMvcTestInfraConfig.class)
@DisplayName("LoginHistoryController WebMvc 테스트")
class LoginHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoginHistoryService service;

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/audit/login-history
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /audit/login-history — AUDIT:READ 권한으로 200 + 페이징 응답 반환")
    void list_returns200WithPagedResult() throws Exception {
        PageResponse<LoginHistoryEntry> page = PageResponse.of(List.of(sampleEntry()), 0, 20, 1L);
        when(service.findPage(eq(0), eq(20), anyString(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/login-history")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"),
                                        Set.of("AUDIT:READ")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /audit/login-history — AUDIT:READ 권한 없으면 403 반환")
    void list_returns403WithoutAuditRead() throws Exception {
        mockMvc.perform(get("/api/v1/audit/login-history")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(2L, "user", Set.of("USER"),
                                        Set.of("USER:READ")))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /audit/login-history — userId 파라미터 서비스로 전달")
    void list_propagatesFilterParams() throws Exception {
        PageResponse<LoginHistoryEntry> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(service.findPage(eq(0), eq(20), anyString(), eq(5L), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(empty);

        mockMvc.perform(get("/api/v1/audit/login-history")
                        .param("userId", "5")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"),
                                        Set.of("AUDIT:READ")))))
                .andExpect(status().isOk());

        verify(service).findPage(0, 20, "createdAt,desc", 5L, null, null, null, null, null);
    }

    @Test
    @DisplayName("GET /audit/login-history — from/to ISO-8601 날짜 파라미터 파싱")
    void list_parsesDateRangeParams() throws Exception {
        PageResponse<LoginHistoryEntry> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(service.findPage(anyInt(), anyInt(), anyString(), isNull(), isNull(), isNull(),
                any(), any(), isNull()))
                .thenReturn(empty);

        mockMvc.perform(get("/api/v1/audit/login-history")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"),
                                        Set.of("AUDIT:READ")))))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private LoginHistoryEntry sampleEntry() {
        return new LoginHistoryEntry(
                1L, 10L, "testuser",
                "127.0.0.1", "Mozilla/5.0",
                true, null, Instant.now()
        );
    }
}
