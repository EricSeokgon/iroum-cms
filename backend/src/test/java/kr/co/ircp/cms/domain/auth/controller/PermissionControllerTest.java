package kr.co.ircp.cms.domain.auth.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PermissionSummary;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PermissionController @WebMvcTest (GREEN 단계).
 *
 * <p>SPEC-CMS-002 REQ-AUTH-013 — 권한 카탈로그 조회 HTTP 계층 검증.
 * 클래스 레벨 @PreAuthorize("hasRole('SUPER_ADMIN')") 동작 확인 포함.
 */
@WebMvcTest(PermissionController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PermissionController GREEN 테스트 (REQ-AUTH-013)")
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PermissionService permissionService;

    private static final JwtPrincipal SUPER_ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("SUPER_ADMIN"), Set.of());

    private static final JwtPrincipal NORMAL_USER_PRINCIPAL =
            new JwtPrincipal(2L, "user", Set.of("USER"), Set.of("USER:READ"));

    private static PermissionSummary sample(String code, String resource, String action) {
        return new PermissionSummary(code, resource, action, code + " 설명");
    }

    @Test
    @DisplayName("GET /api/v1/permissions — SUPER_ADMIN이면 200 OK + 권한 목록")
    void list_returnsOkWithPermissionList() throws Exception {
        List<PermissionSummary> permissions = List.of(
                sample("USER:READ", "USER", "READ"),
                sample("USER:WRITE", "USER", "WRITE"),
                sample("ROLE:READ", "ROLE", "READ")
        );
        when(permissionService.findAll()).thenReturn(permissions);

        mockMvc.perform(get("/api/v1/permissions")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(SUPER_ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("USER:READ"))
                .andExpect(jsonPath("$[0].resource").value("USER"))
                .andExpect(jsonPath("$[0].action").value("READ"))
                .andExpect(jsonPath("$[2].code").value("ROLE:READ"));
    }

    @Test
    @DisplayName("GET /api/v1/permissions — 빈 목록 200 OK")
    void list_returnsOkWithEmptyList() throws Exception {
        when(permissionService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/permissions")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(SUPER_ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/permissions — 권한 description 정상 반환")
    void list_returnsDescriptionField() throws Exception {
        when(permissionService.findAll()).thenReturn(List.of(
                new PermissionSummary("AUDIT:READ", "AUDIT", "READ", "감사 로그 조회 권한")
        ));

        mockMvc.perform(get("/api/v1/permissions")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(SUPER_ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("감사 로그 조회 권한"));
    }

    @Test
    @DisplayName("GET /api/v1/permissions — SUPER_ADMIN 아니면 403 Forbidden")
    void list_returnsForbidden_whenNotSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(NORMAL_USER_PRINCIPAL))))
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
