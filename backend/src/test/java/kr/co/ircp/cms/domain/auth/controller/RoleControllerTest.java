package kr.co.ircp.cms.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.RoleCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.RoleDetail;
import kr.co.ircp.cms.domain.auth.dto.RoleSummary;
import kr.co.ircp.cms.domain.auth.exception.RoleHasUsersException;
import kr.co.ircp.cms.domain.auth.exception.SystemRoleProtectedException;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.RoleService;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RoleController @WebMvcTest (GREEN 단계).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — HTTP 계층(상태 코드, JSON 구조) 검증.
 * Security 비활성화 후 JwtPrincipal 직접 주입.
 */
@WebMvcTest(RoleController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("RoleController GREEN 단계 테스트")
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of());

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/roles
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/roles — 200 OK + 역할 목록 반환")
    void list_returns200WithRoleSummaryList() throws Exception {
        List<RoleSummary> roles = List.of(
                new RoleSummary("SUPER_ADMIN", "최고관리자", null, true, null, 1, 15, Instant.now()),
                new RoleSummary("EDITOR", "편집자", null, false, null, 3, 2, Instant.now())
        );
        when(roleService.findAll()).thenReturn(roles);

        mockMvc.perform(get("/api/v1/roles")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$[1].code").value("EDITOR"));
    }

    // ──────────────────────────────────────────────────────────────
    // GET /api/v1/roles/{code}
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/roles/{code} — 200 OK + 역할 상세")
    void detail_returns200WithRoleDetail() throws Exception {
        RoleDetail detail = new RoleDetail("SUPER_ADMIN", "최고관리자", null,
                true, null, 1, Set.of("USER:READ", "USER:WRITE"), Instant.now());
        when(roleService.findByCode("SUPER_ADMIN")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/roles/SUPER_ADMIN")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.isSystem").value(true));
    }

    // ──────────────────────────────────────────────────────────────
    // POST /api/v1/roles
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/roles — 201 Created")
    void create_returns201() throws Exception {
        RoleCreateRequest req = new RoleCreateRequest(
                "NEW_ROLE", "새역할", "설명", Set.of("USER:READ"));
        RoleDetail detail = new RoleDetail("NEW_ROLE", "새역할", "설명",
                false, null, 0, Set.of("USER:READ"), Instant.now());
        when(roleService.create(any(RoleCreateRequest.class), anyLong())).thenReturn(detail);

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("NEW_ROLE"));
    }

    // ──────────────────────────────────────────────────────────────
    // DELETE /api/v1/roles/{code}
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/roles/{code} — 400 시스템 역할 보호")
    void delete_returns400_whenSystemRole() throws Exception {
        doThrow(new SystemRoleProtectedException("SUPER_ADMIN"))
                .when(roleService).delete(eq("SUPER_ADMIN"), anyLong());

        mockMvc.perform(delete("/api/v1/roles/SUPER_ADMIN")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_SYSTEM_PROTECTED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{code} — 409 사용자 매핑 존재")
    void delete_returns409_whenRoleHasUsers() throws Exception {
        doThrow(new RoleHasUsersException("EDITOR", 3))
                .when(roleService).delete(eq("EDITOR"), anyLong());

        mockMvc.perform(delete("/api/v1/roles/EDITOR")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_HAS_USERS"));
    }

    @Test
    @DisplayName("DELETE /api/v1/roles/{code} — 204 No Content")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/roles/CUSTOM_ROLE")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isNoContent());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오
    // 클래스 레벨 @PreAuthorize("hasRole('SUPER_ADMIN')") 정책 검증
    // (AUTHZ-MATRIX-001 IT 레이어와 분리: 슬라이스 vs SpringBootTest)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-COV-001-1 — GET /api/v1/roles 인증 없이 접근 시 403 Forbidden (@WebMvcTest 한계)")
    void list_returns403_withoutAuthentication() throws Exception {
        // @WebMvcTest + SecurityAutoConfiguration 제외 → SecurityFilterChain 없음 → @PreAuthorize 거부 → 403
        // 401 검증은 SecurityConfig 통합 테스트에서 별도 (REQ-IRR-003).
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"WRONG_AUTHORITY"})
    @DisplayName("AC-COV-001-2 — GET /api/v1/roles 권한 부족 시 403 Forbidden")
    void list_returns403_withInsufficientAuthority() throws Exception {
        // given: WRONG_AUTHORITY는 ROLE_SUPER_ADMIN 정책 미충족
        // when & then: @PreAuthorize 거부 → AccessDeniedHandler → 403
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isForbidden());
    }

    // ─── 헬퍼 ───────────────────────────────────────────────────

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken jwtAuth(
            JwtPrincipal principal) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null,
                principal.roles().stream()
                        .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                        .toList()
        );
    }
}
