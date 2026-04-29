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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MyLoginHistoryController @WebMvcTest.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 본인 로그인 이력 HTTP 계층 검증.
 */
@WebMvcTest(MyLoginHistoryController.class)
@DisplayName("MyLoginHistoryController WebMvc 테스트")
class MyLoginHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoginHistoryService service;

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/me/login-history
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me/login-history — 인증 사용자 본인 userId로 서비스 호출 후 200 반환")
    void myHistory_returns200WithOwnUserId() throws Exception {
        PageResponse<LoginHistoryEntry> page = PageResponse.of(
                List.of(sampleEntry(99L)), 0, 20, 1L);
        when(service.findByUserId(eq(99L), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/me/login-history")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(99L, "testUser", Set.of("USER"), Set.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(service).findByUserId(99L, 0, 20);
    }

    @Test
    @DisplayName("GET /me/login-history — 미인증 요청 401 반환")
    void myHistory_returns401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/login-history")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private LoginHistoryEntry sampleEntry(long userId) {
        return new LoginHistoryEntry(
                1L, userId, "testUser",
                "127.0.0.1", "Mozilla/5.0",
                true, null, Instant.now()
        );
    }
}
