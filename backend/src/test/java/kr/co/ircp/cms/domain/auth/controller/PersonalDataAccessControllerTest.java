package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PersonalDataAccessLogService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PersonalDataAccessController WebMvc 슬라이스 테스트.
 *
 * <p>REQ-AUTH-018-D-2 — 관리자 전용 엔드포인트 접근 제어 검증.
 */
@WebMvcTest({PersonalDataAccessController.class, MyPersonalDataAccessController.class})
@Import(WebMvcTestInfraConfig.class)
@DisplayName("PersonalDataAccessController WebMvc 테스트")
class PersonalDataAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonalDataAccessLogService service;

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/audit/personal-data-access
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /audit/personal-data-access — AUDIT:READ+USER:READ 권한으로 200 반환")
    void list_returns200_withRequiredAuthorities() throws Exception {
        PageResponse<PersonalDataAccessEntry> page = PageResponse.of(
                List.of(sampleEntry()), 0, 20, 1L);
        when(service.findPage(anyInt(), anyInt(), anyString(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/personal-data-access")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"),
                                        Set.of("AUDIT:READ", "USER:READ")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /audit/personal-data-access — AUDIT:READ 권한 없으면 403 반환")
    void list_returns403_withoutAuditRead() throws Exception {
        mockMvc.perform(get("/api/v1/audit/personal-data-access")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(2L, "user", Set.of("USER"),
                                        Set.of("USER:READ")))))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/me/personal-data-access
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me/personal-data-access — 인증 사용자 본인 이력 조회 200 반환")
    void myAccess_returns200_withPaged() throws Exception {
        PageResponse<PersonalDataAccessEntry> page = PageResponse.of(
                List.of(sampleEntry()), 0, 20, 1L);
        when(service.findByTarget(anyLong(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/me/personal-data-access")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user(
                                new JwtPrincipal(99L, "testUser", Set.of("USER"),
                                        Set.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ──────────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────────

    private PersonalDataAccessEntry sampleEntry() {
        return new PersonalDataAccessEntry(
                1L, 10L, "admin", "SUPER_ADMIN",
                20L, "targetUser",
                List.of("email", "name"),
                "BUSINESS_INQUIRY",
                "127.0.0.1", "Mozilla/5.0",
                Instant.now()
        );
    }
}
