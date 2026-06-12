package kr.co.ircp.cms.domain.auth.menu;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-RBAC-001 — RBAC 신규 엔드포인트 + 시드 통합 테스트.
 *
 * <p>커버 AC:
 * AC-001-1/2(ADMIN 역할·권한 시드), AC-002-2/3/4(접근 가능 메뉴 OR 필터·무매핑·트리),
 * AC-003-1/2(현재 사용자 유효 권한 + alias 해소).
 *
 * <p>인가: 신규 엔드포인트(/api/v1/me/permissions, /api/v1/admin/menus/accessible)는
 * SecurityConfig {@code .anyRequest().authenticated()} HTTP 레벨 보호만 사용한다.
 * 메소드 레벨 @PreAuthorize 미부착 → AuthorizationCoverageArchTest baseline(126/138/어휘) 무영향.
 * auth.menu 패키지 배치 → security 패키지 스캔 endpoint baseline 무영향.
 */
// @MX:NOTE: [AUTO] RbacEndpointsIT — SPEC-CMS-RBAC-001 신규 엔드포인트·시드 통합 검증
// @MX:SPEC: SPEC-CMS-RBAC-001
@AutoConfigureMockMvc
@DisplayName("RBAC 엔드포인트·시드 IT (SPEC-CMS-RBAC-001)")
class RbacEndpointsIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-rbac-it-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long userId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userId = insertUser("rbac-user-" + suffix);
    }

    // ─── AC-001: ADMIN 역할·권한 시드 (V48) ──────────────────────────────────

    @Nested
    @DisplayName("AC-001 ADMIN 역할 시드")
    class AdminRoleSeed {

        @Test
        @DisplayName("AC-001-1: roles 테이블에 ADMIN 행 존재 (name='관리자', is_system=TRUE, aliased_to IS NULL)")
        void adminRoleExists() {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM roles WHERE code='ADMIN' AND name='관리자' " +
                    "AND is_system = TRUE AND aliased_to IS NULL",
                    Integer.class);
            assert count != null && count == 1 : "ADMIN 역할 시드가 존재해야 함 (실제: " + count + ")";
        }

        @Test
        @DisplayName("AC-001-2: ADMIN 권한 매핑 — USER:READ/WRITE, ROLE:READ, AUDIT:READ 포함")
        void adminPermissionsIncluded() {
            Integer included = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM role_permissions WHERE role_code='ADMIN' " +
                    "AND permission_code IN ('USER:READ','USER:WRITE','ROLE:READ','AUDIT:READ')",
                    Integer.class);
            assert included != null && included == 4 : "ADMIN 핵심 권한 4종이 매핑되어야 함 (실제: " + included + ")";
        }

        @Test
        @DisplayName("AC-001-2-B: ADMIN 권한 매핑 — SYSTEM:ADMIN/USER:DELETE/ORGANIZATION:DELETE/ROLE:WRITE 미포함")
        void adminPermissionsExcluded() {
            Integer excluded = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM role_permissions WHERE role_code='ADMIN' " +
                    "AND permission_code IN ('SYSTEM:ADMIN','USER:DELETE','ORGANIZATION:DELETE','ROLE:WRITE')",
                    Integer.class);
            assert excluded != null && excluded == 0 : "ADMIN 은 SUPER_ADMIN 전용 권한을 보유하면 안 됨 (실제: " + excluded + ")";
        }
    }

    // ─── AC-003: 현재 사용자 유효 권한 ───────────────────────────────────────

    @Nested
    @DisplayName("AC-003 현재 사용자 유효 권한")
    class MePermissions {

        @Test
        @DisplayName("AC-003-1: ADMIN 사용자 GET /me/permissions → roles=[ADMIN] + ADMIN 매핑 권한")
        void admin_returnsRolesAndPermissions() throws Exception {
            assignRole(userId, "ADMIN");
            givenUserToken(userId, Set.of("ADMIN"));

            mockMvc.perform(get("/api/v1/me/permissions")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles").isArray())
                    .andExpect(jsonPath("$.roles[?(@ == 'ADMIN')]").exists())
                    .andExpect(jsonPath("$.permissions[?(@ == 'USER:READ')]").exists())
                    .andExpect(jsonPath("$.permissions[?(@ == 'ROLE:READ')]").exists())
                    .andExpect(jsonPath("$.permissions[?(@ == 'AUDIT:READ')]").exists())
                    .andExpect(jsonPath("$.permissions[?(@ == 'SYSTEM:ADMIN')]").doesNotExist());
        }

        @Test
        @DisplayName("AC-003-2: SYSADMIN(alias→SUPER_ADMIN) 사용자 → SUPER_ADMIN 전체 권한 집합")
        void sysadminAlias_returnsSuperAdminPermissions() throws Exception {
            assignRole(userId, "SYSADMIN");
            givenUserToken(userId, Set.of("SYSADMIN"));

            mockMvc.perform(get("/api/v1/me/permissions")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    // SUPER_ADMIN 전용 권한이 alias 해소로 포함되어야 함
                    .andExpect(jsonPath("$.permissions[?(@ == 'SYSTEM:ADMIN')]").exists())
                    .andExpect(jsonPath("$.permissions[?(@ == 'USER:DELETE')]").exists());
        }

        @Test
        @DisplayName("AC-003-3: 비인증 GET /me/permissions → 401")
        void unauthenticated_returns401() throws Exception {
            int code = mockMvc.perform(get("/api/v1/me/permissions"))
                    .andReturn().getResponse().getStatus();
            assert code == 401 : "비인증 응답은 401 이어야 함 (실제: " + code + ")";
        }
    }

    // ─── AC-002: 접근 가능 어드민 메뉴 ───────────────────────────────────────

    @Nested
    @DisplayName("AC-002 접근 가능 어드민 메뉴")
    class AccessibleMenus {

        @Test
        @DisplayName("AC-002-2: ROLE:READ 보유 사용자 → system.roles 포함")
        void roleReadUser_includesRolesMenu() throws Exception {
            // VIEWER 권한 집합은 USER:READ/ORGANIZATION:READ 만 → ADMIN 으로 ROLE:READ 확보
            assignRole(userId, "ADMIN");
            givenUserToken(userId, Set.of("ADMIN"));

            mockMvc.perform(get("/api/v1/admin/menus/accessible")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.menuKey == 'system.roles')]").exists());
        }

        @Test
        @DisplayName("AC-002-2-B: AUDIT:READ 미보유(EDITOR) 사용자 → audit 그룹 미포함")
        void noAuditPermission_excludesAuditMenu() throws Exception {
            // EDITOR 권한: USER:READ, ORGANIZATION:READ (ROLE:READ/AUDIT:READ 없음)
            assignRole(userId, "EDITOR");
            givenUserToken(userId, Set.of("EDITOR"));

            mockMvc.perform(get("/api/v1/admin/menus/accessible")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.menuKey == 'system.roles')]").doesNotExist())
                    .andExpect(jsonPath("$..[?(@.menuKey == 'audit.permission_changes')]").doesNotExist());
        }

        @Test
        @DisplayName("AC-002-3: 무매핑 메뉴(users)는 인증된 모든 관리자에게 노출")
        void unmappedMenu_visibleToAllAuth() throws Exception {
            assignRole(userId, "EDITOR");
            givenUserToken(userId, Set.of("EDITOR"));

            mockMvc.perform(get("/api/v1/admin/menus/accessible")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.menuKey == 'users')]").exists())
                    .andExpect(jsonPath("$[?(@.menuKey == 'dashboard')]").exists());
        }

        @Test
        @DisplayName("AC-002-4: 자식이 접근 가능하면 부모도 트리로 함께 반환 (audit 그룹)")
        void accessibleChild_includesParentTree() throws Exception {
            assignRole(userId, "ADMIN"); // AUDIT:READ 보유
            givenUserToken(userId, Set.of("ADMIN"));

            mockMvc.perform(get("/api/v1/admin/menus/accessible")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    // audit 부모 노드 존재 + children 에 permission_changes 포함
                    .andExpect(jsonPath("$[?(@.menuKey == 'audit')]").exists())
                    .andExpect(jsonPath("$[?(@.menuKey == 'audit')].children[?(@.menuKey == 'audit.permission_changes')]").exists());
        }

        @Test
        @DisplayName("AC-002-A: 비인증 GET /admin/menus/accessible → 401")
        void unauthenticated_returns401() throws Exception {
            int code = mockMvc.perform(get("/api/v1/admin/menus/accessible"))
                    .andReturn().getResponse().getStatus();
            assert code == 401 : "비인증 응답은 401 이어야 함 (실제: " + code + ")";
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "rbac-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', 'RBAC테스트사용자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private void assignRole(long id, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO user_roles (user_id, role_code) VALUES (?, ?) " +
                "ON CONFLICT (user_id, role_code) DO NOTHING",
                id, roleCode);
    }
}
