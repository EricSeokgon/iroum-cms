package kr.co.ircp.cms.security;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003 RUN Step 2 — HTTP 권한 매트릭스 IT 확장 3차.
 *
 * <p>본 IT는 AUTHZ-IT-EXPAND-001/002에서 커버하지 못한 운영 ~60 endpoint를 추가하여,
 * AuthorizationCoverageArchTest baseline 54 → 120+ endpoint 갱신 + ArchUnit baseline
 * 100% IT 매핑 + OWASP A01 완전 검출 능력 달성한다.
 *
 * <p>패턴: AUTHZ-MATRIX-001 + EXPAND-001 + EXPAND-002 + REGRESSION-001 인프라 100% 재사용.
 *
 * <h3>본 SPEC v0.2 Appendix A 운영 endpoint 인벤토리 결과</h3>
 *
 * <table border="1" summary="EXPAND-003 Phase 분할">
 *   <thead>
 *     <tr><th>Phase</th><th>대상 Controller</th><th>예상 endpoint</th><th>도메인 그룹</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>Phase A (30)</td><td>Org/User/Page/Code/CodeGroup 미커버</td><td>~30</td><td>§A.1-A.3</td></tr>
 *     <tr><td>Phase B (30)</td><td>Menu/Maintenance/Widget/Setting/Banner 미커버</td><td>~30</td><td>§A.4-A.6</td></tr>
 *     <tr><td>Phase C (잔여)</td><td>Search/Synonym/Permission/Governance/Stats 미커버</td><td>~10-20</td><td>§A.7-A.8</td></tr>
 *   </tbody>
 * </table>
 *
 * <p><b>합계</b>: ~60 endpoint × 3 시나리오 ≈ 180~200 AC (Phase A-C 단계 활성화)
 *
 * <h3>본 SPEC 완성 시 OWASP A01 회귀 검출 6중 검증</h3>
 * <ul>
 *   <li>HTTP 1차 (AUTHZ-MATRIX-001): 19 AC, 6 endpoint</li>
 *   <li>HTTP 확장 1차 (AUTHZ-IT-EXPAND-001): 88 AC, 29 endpoint, 12 어휘</li>
 *   <li>HTTP 확장 2차 (AUTHZ-IT-EXPAND-002): 57 AC, 19 endpoint, 19 어휘</li>
 *   <li>HTTP 확장 3차 (AUTHZ-IT-EXPAND-003 — 본 SPEC): ~180 AC, ~60 endpoint, 모든 어휘 추가 endpoint</li>
 *   <li>메소드 슬라이스 (CTRL-AUTHZ-COVERAGE-001): 31 보강</li>
 *   <li>ArchUnit 자동 검출 (AUTHZ-AUTODETECT-001): 4 AC, 120+ endpoint baseline</li>
 * </ul>
 *
 * <p>합계: <b>~380+ AC</b> + ArchUnit baseline 100% IT 매핑 달성.
 *
 * <h3>Step 2 — 인프라 신설 + smoke test만 활성화</h3>
 *
 * <p>본 RUN Step 2에서는 IT 클래스 부팅 + JWT Mock 주입 검증의 smoke test 1건만 활성화한다.
 * ~60 endpoint × 3 시나리오는 Step 3 (Phase A) / Step 4 (Phase B) / Step 5 (Phase C)에서 점진 활성화한다.
 *
 * <h3>패턴 재사용</h3>
 * <ul>
 *   <li>{@link AuthorizationMatrixExpand2IT}의 helper 패턴 100% 재사용 (assertAuthzPassed 등)</li>
 *   <li>REGRESSION-001 v0.5/v0.6/v0.8에서 검증된 응답 코드 분기 (AUTH_REQUIRED 401 / ACCESS_DENIED 403)</li>
 *   <li>DTO 정상 body 정상화 (각 endpoint required fields 충족)</li>
 *   <li>META-IT-GREEN-MANDATORY-001 Sync checklist 4 항목 충족</li>
 * </ul>
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003 (REQ-AM-EXP3-001/002/003/004/005)
 */
// @MX:NOTE: [AUTO] AuthorizationMatrixExpand3IT — ~60 미커버 endpoint IT 매트릭스 (확장 3차)
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("HTTP 권한 매트릭스 IT 확장 3차 (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-003)")
class AuthorizationMatrixExpand3IT {

    // ─── Testcontainers PostgreSQL 16 (AUTHZ-MATRIX/EXPAND-001/002 패턴 일관) ─────────
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iroum_cms_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void overrideDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // PII 더미 키 (32 bytes base64) — SPEC-PII-001 인프라 일관
        registry.add("pii.keyvault.keys.v1", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        registry.add("pii.keyvault.hmac-key", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    @SuppressWarnings("unused") // Step 3~5에서 활성화될 시나리오에서 사용 예정
    private static final String VALID_TOKEN = "valid.jwt.token";

    // ─── JWT Mock helper (AUTHZ-IT-EXPAND-001/002 패턴 100% 재사용) ────────────────
    @SuppressWarnings("unused") // Step 3~5에서 활성화될 시나리오에서 사용 예정
    private void givenValidToken(Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "testuser", roles, permissions, Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    /**
     * 권한 통과 검증 helper — 401/403 아님 + service domain exception 허용.
     *
     * <p>REGRESSION-001 v0.5에서 검증된 패턴. 운영 GlobalExceptionHandler가 IllegalArgumentException/
     * 도메인 RuntimeException을 처리하지 않아 ServletException으로 wrap되는 경우를 권한 통과 IT 본질적
     * PASS로 처리. AccessDeniedException/AuthenticationException은 권한 실패이므로 제외.
     */
    @SuppressWarnings("unused")
    private void assertAuthzPassed(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        try {
            mockMvc.perform(request)
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        } catch (jakarta.servlet.ServletException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return;
            }
            if (cause instanceof RuntimeException
                    && !(cause instanceof org.springframework.security.access.AccessDeniedException)
                    && !(cause instanceof org.springframework.security.core.AuthenticationException)) {
                return;
            }
            throw e;
        }
    }

    // =================================================================================
    // §0 인프라 smoke test — Step 2에서 유일하게 활성화되는 시나리오
    // =================================================================================

    @Test
    @DisplayName("§0 AC-AME3-002-1: 컨텍스트 부팅 + JwtTokenProvider/TokenBlacklistMapper Mock 주입 + JwtTestAuth helper 동작")
    void contextLoadsAndJwtAuthMockable() {
        assertNotNull(mockMvc, "MockMvc 주입 확인 (운영 SecurityFilterChain 적재 결과)");
        assertNotNull(jwtTokenProvider, "JwtTokenProvider @MockitoBean 주입 확인");
        assertNotNull(tokenBlacklistMapper, "TokenBlacklistMapper @MockitoBean 주입 확인");
        givenValidToken(Set.of(), Set.of());
    }

    // =================================================================================
    // §A REQ-AM-EXP3-001 — ~60 미커버 endpoint 매트릭스 (~180 AC, Step 3~5 활성화)
    // =================================================================================

    /** §A.1 OrganizationDomainTests — OrganizationController 7개 미커버 endpoint (Phase A 활성화). */
    @Nested
    @DisplayName("§A.1 OrganizationDomainTests (7 미커버 endpoint, Step 3 Phase A 활성화)")
    class OrganizationDomainTests {

        // ── GET /organizations/tree — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A1-1: GET /api/v1/organizations/tree — Authorization 부재 + 401")
        void orgTree_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/organizations/tree"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-2: GET /api/v1/organizations/tree — USER 역할 + 403 (SUPER_ADMIN/DEPT_ADMIN 부재)")
        void orgTree_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/organizations/tree")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-3: GET /api/v1/organizations/tree — DEPT_ADMIN 보유 + 401/403 아님 (OR bypass)")
        void orgTree_hasDeptAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/organizations/tree")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /organizations — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A1-4: GET /api/v1/organizations — Authorization 부재 + 401")
        void orgList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/organizations"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-5: GET /api/v1/organizations — USER 역할 + 403")
        void orgList_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/organizations")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-6: GET /api/v1/organizations — SUPER_ADMIN 보유 + 401/403 아님")
        void orgList_hasSuperAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/organizations")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /organizations/{id} — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A1-7: GET /api/v1/organizations/{id} — Authorization 부재 + 401")
        void orgGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/organizations/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-8: GET /api/v1/organizations/{id} — USER 역할 + 403")
        void orgGet_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/organizations/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-9: GET /api/v1/organizations/{id} — DEPT_ADMIN 보유 + 401/403 아님")
        void orgGet_hasDeptAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            // 운영 service "조직을 찾을 수 없습니다" IllegalArgumentException 허용
            assertAuthzPassed(get("/api/v1/organizations/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PUT /organizations/{id} — hasRole(SUPER_ADMIN) ──
        private static final String ORG_UPDATE_BODY = "{\"name\":\"업데이트 조직\",\"sortOrder\":1}";

        @Test
        @DisplayName("AC-AME3-A1-10: PUT /api/v1/organizations/{id} — Authorization 부재 + 401")
        void orgUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/organizations/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORG_UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-11: PUT /api/v1/organizations/{id} — DEPT_ADMIN 단독 + 403 (SUPER_ADMIN 부재)")
        void orgUpdate_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(put("/api/v1/organizations/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ORG_UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-12: PUT /api/v1/organizations/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void orgUpdate_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(put("/api/v1/organizations/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ORG_UPDATE_BODY));
        }

        // ── DELETE /organizations/{id} — hasRole(SUPER_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A1-13: DELETE /api/v1/organizations/{id} — Authorization 부재 + 401")
        void orgDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/organizations/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-14: DELETE /api/v1/organizations/{id} — DEPT_ADMIN 단독 + 403")
        void orgDelete_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(delete("/api/v1/organizations/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-15: DELETE /api/v1/organizations/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void orgDelete_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/organizations/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /organizations/{id}/history — hasRole(SUPER_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A1-16: GET /api/v1/organizations/{id}/history — Authorization 부재 + 401")
        void orgHistory_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/organizations/1/history"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-17: GET /api/v1/organizations/{id}/history — DEPT_ADMIN 단독 + 403 (SUPER_ADMIN 부재)")
        void orgHistory_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/organizations/1/history")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-18: GET /api/v1/organizations/{id}/history — SUPER_ADMIN 보유 + 401/403 아님")
        void orgHistory_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(get("/api/v1/organizations/1/history")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── POST /organizations/users/{userId}/organization — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A1-19: POST /api/v1/organizations/users/{userId}/organization — Authorization 부재 + 401")
        void orgAssignUser_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/organizations/users/1/organization")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"organizationId\":1}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A1-20: POST /api/v1/organizations/users/{userId}/organization — USER 역할 + 403")
        void orgAssignUser_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(post("/api/v1/organizations/users/1/organization")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"organizationId\":1}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A1-21: POST /api/v1/organizations/users/{userId}/organization — DEPT_ADMIN 보유 + 401/403 아님")
        void orgAssignUser_hasDeptAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            assertAuthzPassed(post("/api/v1/organizations/users/1/organization")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"organizationId\":1}"));
        }
    }

    /** §A.2 UserDomainTests — UserController 5개 미커버 endpoint (Phase A 활성화). */
    @Nested
    @DisplayName("§A.2 UserDomainTests (5 미커버 endpoint, Step 3 Phase A 활성화)")
    class UserDomainTests {

        // ── GET /users — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A2-1: GET /api/v1/users — Authorization 부재 + 401")
        void userList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A2-2: GET /api/v1/users — USER 역할 + 403")
        void userList_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A2-3: GET /api/v1/users — DEPT_ADMIN 보유 + 401/403 아님 (OR bypass)")
        void userList_hasDeptAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /users/{id} — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A2-4: GET /api/v1/users/{id} — Authorization 부재 + 401")
        void userGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/users/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A2-5: GET /api/v1/users/{id} — USER 역할 + 403")
        void userGet_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/users/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A2-6: GET /api/v1/users/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void userGet_hasSuperAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(get("/api/v1/users/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PUT /users/{id} — hasRole(SUPER_ADMIN) ──
        private static final String USER_UPDATE_BODY = "{\"name\":\"업데이트 사용자\",\"status\":\"ACTIVE\"}";

        @Test
        @DisplayName("AC-AME3-A2-7: PUT /api/v1/users/{id} — Authorization 부재 + 401")
        void userUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(USER_UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A2-8: PUT /api/v1/users/{id} — DEPT_ADMIN 단독 + 403 (SUPER_ADMIN 부재)")
        void userUpdate_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(put("/api/v1/users/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(USER_UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A2-9: PUT /api/v1/users/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void userUpdate_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(put("/api/v1/users/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(USER_UPDATE_BODY));
        }

        // ── DELETE /users/{id} — hasRole(SUPER_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A2-10: DELETE /api/v1/users/{id} — Authorization 부재 + 401")
        void userDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/users/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A2-11: DELETE /api/v1/users/{id} — DEPT_ADMIN 단독 + 403")
        void userDelete_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(delete("/api/v1/users/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A2-12: DELETE /api/v1/users/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void userDelete_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/users/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── POST /users/{id}/unlock — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A2-13: POST /api/v1/users/{id}/unlock — Authorization 부재 + 401")
        void userUnlock_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/users/1/unlock"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A2-14: POST /api/v1/users/{id}/unlock — USER 역할 + 403")
        void userUnlock_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(post("/api/v1/users/1/unlock")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A2-15: POST /api/v1/users/{id}/unlock — DEPT_ADMIN 보유 + 401/403 아님")
        void userUnlock_hasDeptAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            assertAuthzPassed(post("/api/v1/users/1/unlock")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §A.3 CodeDomainTests — Code 3 + CodeGroup 4 미커버 endpoint (Phase A 활성화). */
    @Nested
    @DisplayName("§A.3 CodeDomainTests (Code 3 + CodeGroup 4 미커버, Step 3 Phase A 활성화)")
    class CodeDomainTests {

        // ── GET /system/codes/bulk — hasAuthority(SYSTEM:CODE:READ) ──
        @Test
        @DisplayName("AC-AME3-A3-1: GET /api/v1/system/codes/bulk — Authorization 부재 + 401")
        void codesBulk_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/codes/bulk").param("groups", "GRP1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-2: GET /api/v1/system/codes/bulk — SYSTEM:CODE:READ 부재 + 403")
        void codesBulk_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/system/codes/bulk")
                            .param("groups", "GRP1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-3: GET /api/v1/system/codes/bulk — SYSTEM:CODE:READ 보유 + 401/403 아님")
        void codesBulk_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ"));
            mockMvc.perform(get("/api/v1/system/codes/bulk")
                            .param("groups", "GRP1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /system/codes/{id} — hasAuthority(SYSTEM:CODE:READ) ──
        @Test
        @DisplayName("AC-AME3-A3-4: GET /api/v1/system/codes/{id} — Authorization 부재 + 401")
        void codeGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/codes/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-5: GET /api/v1/system/codes/{id} — SYSTEM:CODE:READ 부재 + 403")
        void codeGet_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/system/codes/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-6: GET /api/v1/system/codes/{id} — SYSTEM:CODE:READ 보유 + 401/403 아님")
        void codeGet_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ"));
            assertAuthzPassed(get("/api/v1/system/codes/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── DELETE /system/codes/{id} — hasAuthority(SYSTEM:CODE:WRITE) ──
        @Test
        @DisplayName("AC-AME3-A3-7: DELETE /api/v1/system/codes/{id} — Authorization 부재 + 401")
        void codeDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/system/codes/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-8: DELETE /api/v1/system/codes/{id} — SYSTEM:CODE:WRITE 부재(READ만) + 403 (어휘 분리)")
        void codeDelete_missingWriteAuthority_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ")); // WRITE 부재
            mockMvc.perform(delete("/api/v1/system/codes/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-9: DELETE /api/v1/system/codes/{id} — SYSTEM:CODE:WRITE 보유 + 401/403 아님")
        void codeDelete_hasWriteAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:WRITE"));
            assertAuthzPassed(delete("/api/v1/system/codes/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /system/codes/groups — hasAuthority(SYSTEM:CODE:READ) ──
        @Test
        @DisplayName("AC-AME3-A3-10: GET /api/v1/system/codes/groups — Authorization 부재 + 401")
        void codeGroupList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/codes/groups"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-11: GET /api/v1/system/codes/groups — SYSTEM:CODE:READ 부재 + 403")
        void codeGroupList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/system/codes/groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-12: GET /api/v1/system/codes/groups — SYSTEM:CODE:READ 보유 + 401/403 아님")
        void codeGroupList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ"));
            mockMvc.perform(get("/api/v1/system/codes/groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /system/codes/groups/{id} — hasAuthority(SYSTEM:CODE:READ) ──
        @Test
        @DisplayName("AC-AME3-A3-13: GET /api/v1/system/codes/groups/{id} — Authorization 부재 + 401")
        void codeGroupGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/codes/groups/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-14: GET /api/v1/system/codes/groups/{id} — SYSTEM:CODE:READ 부재 + 403")
        void codeGroupGet_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/system/codes/groups/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-15: GET /api/v1/system/codes/groups/{id} — SYSTEM:CODE:READ 보유 + 401/403 아님")
        void codeGroupGet_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ"));
            assertAuthzPassed(get("/api/v1/system/codes/groups/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PUT /system/codes/groups/{id} — hasAuthority(SYSTEM:CODE:WRITE) ──
        private static final String CODE_GROUP_BODY = "{\"groupCode\":\"TEST_GRP\",\"name\":\"테스트 그룹\"}";

        @Test
        @DisplayName("AC-AME3-A3-16: PUT /api/v1/system/codes/groups/{id} — Authorization 부재 + 401")
        void codeGroupUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/system/codes/groups/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CODE_GROUP_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-17: PUT /api/v1/system/codes/groups/{id} — SYSTEM:CODE:WRITE 부재 + 403 (분리 회귀)")
        void codeGroupUpdate_missingWriteAuthority_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ"));
            mockMvc.perform(put("/api/v1/system/codes/groups/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CODE_GROUP_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-18: PUT /api/v1/system/codes/groups/{id} — SYSTEM:CODE:WRITE 보유 + 401/403 아님")
        void codeGroupUpdate_hasWriteAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:WRITE"));
            assertAuthzPassed(put("/api/v1/system/codes/groups/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CODE_GROUP_BODY));
        }

        // ── DELETE /system/codes/groups/{id} — hasAuthority(SYSTEM:CODE:WRITE) ──
        @Test
        @DisplayName("AC-AME3-A3-19: DELETE /api/v1/system/codes/groups/{id} — Authorization 부재 + 401")
        void codeGroupDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/system/codes/groups/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A3-20: DELETE /api/v1/system/codes/groups/{id} — SYSTEM:CODE:WRITE 부재 + 403")
        void codeGroupDelete_missingWriteAuthority_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:READ"));
            mockMvc.perform(delete("/api/v1/system/codes/groups/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A3-21: DELETE /api/v1/system/codes/groups/{id} — SYSTEM:CODE:WRITE 보유 + 401/403 아님")
        void codeGroupDelete_hasWriteAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:WRITE"));
            assertAuthzPassed(delete("/api/v1/system/codes/groups/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §A.4 MenuMaintenanceDomainTests — Menu 2 + Maintenance 2 미커버 endpoint (Phase B 활성화). */
    @Nested
    @DisplayName("§A.4 MenuMaintenanceDomainTests (Menu 2 + Maintenance 2 미커버, Step 4 Phase B 활성화)")
    class MenuMaintenanceDomainTests {

        // ── PATCH /menus/{id}/move — hasAuthority(MENU:WRITE) ──
        @Test
        @DisplayName("AC-AME3-A4-1: PATCH /api/v1/content/menus/{id}/move — Authorization 부재 + 401")
        void menuMove_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/content/menus/1/move")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newParentId\":null}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A4-2: PATCH /api/v1/content/menus/{id}/move — MENU:WRITE 부재 + 403")
        void menuMove_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(patch("/api/v1/content/menus/1/move")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newParentId\":null}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A4-3: PATCH /api/v1/content/menus/{id}/move — MENU:WRITE 보유 + 401/403 아님")
        void menuMove_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("MENU:WRITE"));
            assertAuthzPassed(patch("/api/v1/content/menus/1/move")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newParentId\":null}"));
        }

        // ── PATCH /menus/{id}/visibility — hasAuthority(MENU:WRITE) ──
        @Test
        @DisplayName("AC-AME3-A4-4: PATCH /api/v1/content/menus/{id}/visibility — Authorization 부재 + 401")
        void menuVisibility_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/content/menus/1/visibility").param("isVisible", "true"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A4-5: PATCH /api/v1/content/menus/{id}/visibility — MENU:WRITE 부재 + 403")
        void menuVisibility_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(patch("/api/v1/content/menus/1/visibility")
                            .param("isVisible", "true")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A4-6: PATCH /api/v1/content/menus/{id}/visibility — MENU:WRITE 보유 + 401/403 아님")
        void menuVisibility_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("MENU:WRITE"));
            assertAuthzPassed(patch("/api/v1/content/menus/1/visibility")
                    .param("isVisible", "true")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /system/maintenance/{id} — hasAuthority(SYSTEM:MAINT:READ) ──
        @Test
        @DisplayName("AC-AME3-A4-7: GET /api/v1/system/maintenance/{id} — Authorization 부재 + 401")
        void maintGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/maintenance/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A4-8: GET /api/v1/system/maintenance/{id} — SYSTEM:MAINT:READ 부재 + 403")
        void maintGet_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/system/maintenance/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A4-9: GET /api/v1/system/maintenance/{id} — SYSTEM:MAINT:READ 보유 + 401/403 아님")
        void maintGet_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:MAINT:READ"));
            assertAuthzPassed(get("/api/v1/system/maintenance/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── POST /system/maintenance/{id}/activate — hasAuthority(SYSTEM:MAINT:WRITE) ──
        @Test
        @DisplayName("AC-AME3-A4-10: POST /api/v1/system/maintenance/{id}/activate — Authorization 부재 + 401")
        void maintActivate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/system/maintenance/1/activate"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A4-11: POST /api/v1/system/maintenance/{id}/activate — SYSTEM:MAINT:WRITE 부재(READ만) + 403 (분리 회귀)")
        void maintActivate_missingWriteAuthority_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:MAINT:READ")); // WRITE 부재
            mockMvc.perform(post("/api/v1/system/maintenance/1/activate")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A4-12: POST /api/v1/system/maintenance/{id}/activate — SYSTEM:MAINT:WRITE 보유 + 401/403 아님")
        void maintActivate_hasWriteAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:MAINT:WRITE"));
            assertAuthzPassed(post("/api/v1/system/maintenance/1/activate")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §A.5 DashboardWidgetDomainTests — Widget 2 미커버 endpoint (Phase B 활성화, Setting은 EXPAND-002 100% 커버). */
    @Nested
    @DisplayName("§A.5 DashboardWidgetDomainTests (Widget 2 미커버, Step 4 Phase B 활성화)")
    class DashboardWidgetSettingDomainTests {

        // WidgetRequest required: code, name, widgetType, dataSource, dataSourceConfig
        private static final String WIDGET_BODY = "{\"code\":\"TEST_W\",\"name\":\"테스트 위젯\","
                + "\"widgetType\":\"CHART\",\"dataSource\":\"sql\",\"dataSourceConfig\":\"{}\"}";

        // ── DELETE /widgets/{id} — hasRole(SUPER_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A5-1: DELETE /api/v1/dashboard/widgets/{id} — Authorization 부재 + 401")
        void widgetDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/dashboard/widgets/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A5-2: DELETE /api/v1/dashboard/widgets/{id} — DEPT_ADMIN 단독 + 403 (SUPER_ADMIN 부재)")
        void widgetDelete_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(delete("/api/v1/dashboard/widgets/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A5-3: DELETE /api/v1/dashboard/widgets/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void widgetDelete_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/dashboard/widgets/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── POST /widgets/preview — hasAnyRole(SUPER_ADMIN, DEPT_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A5-4: POST /api/v1/dashboard/widgets/preview — Authorization 부재 + 401")
        void widgetPreview_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/dashboard/widgets/preview")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(WIDGET_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A5-5: POST /api/v1/dashboard/widgets/preview — USER 역할 + 403")
        void widgetPreview_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(post("/api/v1/dashboard/widgets/preview")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(WIDGET_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A5-6: POST /api/v1/dashboard/widgets/preview — DEPT_ADMIN 보유 + 401/403 아님")
        void widgetPreview_hasDeptAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            assertAuthzPassed(post("/api/v1/dashboard/widgets/preview")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(WIDGET_BODY));
        }
    }

    /** §A.6 BannerI18nDomainTests — Banner 1 + I18n 1 미커버 (Phase B 활성화, Site는 EXPAND-002 100% 커버). */
    @Nested
    @DisplayName("§A.6 BannerI18nDomainTests (Banner 1 + I18n 1 미커버, Step 4 Phase B 활성화)")
    class BannerSiteI18nDomainTests {

        // ── DELETE /banners/{id} — hasAuthority(CONTENT:WRITE) ──
        @Test
        @DisplayName("AC-AME3-A6-1: DELETE /api/v1/content/banners/{id} — Authorization 부재 + 401")
        void bannerDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/content/banners/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A6-2: DELETE /api/v1/content/banners/{id} — CONTENT:WRITE 부재 + 403")
        void bannerDelete_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(delete("/api/v1/content/banners/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A6-3: DELETE /api/v1/content/banners/{id} — CONTENT:WRITE 보유 + 401/403 아님")
        void bannerDelete_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));
            assertAuthzPassed(delete("/api/v1/content/banners/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PUT /content/i18n — hasAuthority(CONTENT:WRITE) ──
        private static final String I18N_BODY = "{\"items\":[]}";

        @Test
        @DisplayName("AC-AME3-A6-4: PUT /api/v1/content/i18n — Authorization 부재 + 401")
        void i18nBulkUpsert_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/content/i18n")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(I18N_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A6-5: PUT /api/v1/content/i18n — CONTENT:WRITE 부재(CONTENT:READ만) + 403 (분리 회귀)")
        void i18nBulkUpsert_missingWriteAuthority_returns403() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:READ")); // WRITE 부재
            mockMvc.perform(put("/api/v1/content/i18n")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(I18N_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A6-6: PUT /api/v1/content/i18n — CONTENT:WRITE 보유 + 401/403 아님")
        void i18nBulkUpsert_hasWriteAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));
            assertAuthzPassed(put("/api/v1/content/i18n")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(I18N_BODY));
        }
    }

    /** §A.7 SearchPermissionDomainTests — Search 1 + Synonym 1 + Permission 1 미커버 (Phase C 활성화). */
    @Nested
    @DisplayName("§A.7 SearchPermissionDomainTests (Search 1 + Synonym 1 + Permission 1 미커버, Step 5 Phase C 활성화)")
    class SearchPermissionDomainTests {

        // ── GET /api/v1/permissions — 클래스 레벨 hasRole(SUPER_ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A7-1: GET /api/v1/permissions — Authorization 부재 + 401")
        void permissionList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/permissions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A7-2: GET /api/v1/permissions — DEPT_ADMIN 단독 + 403 (SUPER_ADMIN 부재)")
        void permissionList_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/permissions")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A7-3: GET /api/v1/permissions — SUPER_ADMIN 보유 + 401/403 아님 (class-level)")
        void permissionList_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/permissions")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /api/v1/search/synonyms — 클래스 레벨 hasRole(ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A7-4: GET /api/v1/search/synonyms — Authorization 부재 + 401")
        void synonymList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/search/synonyms"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A7-5: GET /api/v1/search/synonyms — USER 역할 + 403")
        void synonymList_missingAdminRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/search/synonyms")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A7-6: GET /api/v1/search/synonyms — ADMIN 보유 + 401/403 아님 (class-level)")
        void synonymList_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/search/synonyms")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /api/v1/search/stats/queries — 메소드 레벨 hasRole(ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A7-7: GET /api/v1/search/stats/queries — Authorization 부재 + 401")
        void searchStats_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/search/stats/queries"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A7-8: GET /api/v1/search/stats/queries — USER 역할 + 403")
        void searchStats_missingAdminRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/search/stats/queries")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A7-9: GET /api/v1/search/stats/queries — ADMIN 보유 + 401/403 아님")
        void searchStats_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/search/stats/queries")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    /** §A.8 GovernanceStatsDomainTests — Governance 3 + Stats 3 미커버 (Phase C 활성화). */
    @Nested
    @DisplayName("§A.8 GovernanceStatsDomainTests (Governance 3 + Stats 3 미커버, Step 5 Phase C 활성화)")
    class GovernanceStatsDomainTests {

        // ── GET /api/v1/governance/batch-logs — 클래스 레벨 hasRole(ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A8-1: GET /api/v1/governance/batch-logs — Authorization 부재 + 401")
        void batchLogs_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/governance/batch-logs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A8-2: GET /api/v1/governance/batch-logs — USER 역할 + 403")
        void batchLogs_missingAdminRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/governance/batch-logs")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A8-3: GET /api/v1/governance/batch-logs — ADMIN 보유 + 401/403 아님 (class-level)")
        void batchLogs_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/governance/batch-logs")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /api/v1/governance/dictionary — 클래스 레벨 hasRole(ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A8-4: GET /api/v1/governance/dictionary — Authorization 부재 + 401")
        void dictionary_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/governance/dictionary"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A8-5: GET /api/v1/governance/dictionary — USER 역할 + 403")
        void dictionary_missingAdminRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/governance/dictionary")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A8-6: GET /api/v1/governance/dictionary — ADMIN 보유 + 401/403 아님 (class-level)")
        void dictionary_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/governance/dictionary")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /api/v1/governance/stats/policies — 클래스 레벨 hasRole(ADMIN) ──
        @Test
        @DisplayName("AC-AME3-A8-7: GET /api/v1/governance/stats/policies — Authorization 부재 + 401")
        void governanceStats_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/governance/stats/policies"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A8-8: GET /api/v1/governance/stats/policies — USER 역할 + 403")
        void governanceStats_missingAdminRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/governance/stats/policies")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A8-9: GET /api/v1/governance/stats/policies — ADMIN 보유 + 401/403 아님")
        void governanceStats_hasAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            mockMvc.perform(get("/api/v1/governance/stats/policies")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── GET /api/v1/system/stats/top-pages — hasAuthority(SYSTEM:STATS) ──
        @Test
        @DisplayName("AC-AME3-A8-10: GET /api/v1/system/stats/top-pages — Authorization 부재 + 401")
        void statsTopPages_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/stats/top-pages"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A8-11: GET /api/v1/system/stats/top-pages — SYSTEM:STATS 부재 + 403")
        void statsTopPages_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/system/stats/top-pages")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A8-12: GET /api/v1/system/stats/top-pages — SYSTEM:STATS 보유 + 401/403 아님")
        void statsTopPages_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:STATS"));
            assertAuthzPassed(get("/api/v1/system/stats/top-pages")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── POST /api/v1/system/stats/recompute — hasAuthority(SYSTEM:STATS) ──
        @Test
        @DisplayName("AC-AME3-A8-13: POST /api/v1/system/stats/recompute — Authorization 부재 + 401")
        void statsRecompute_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/system/stats/recompute")
                            .param("from", "2026-01-01").param("to", "2026-01-31"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME3-A8-14: POST /api/v1/system/stats/recompute — SYSTEM:STATS 부재 + 403")
        void statsRecompute_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(post("/api/v1/system/stats/recompute")
                            .param("from", "2026-01-01").param("to", "2026-01-31")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME3-A8-15: POST /api/v1/system/stats/recompute — SYSTEM:STATS 보유 + 401/403 아님")
        void statsRecompute_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:STATS"));
            assertAuthzPassed(post("/api/v1/system/stats/recompute")
                    .param("from", "2026-01-01").param("to", "2026-01-31")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }
}
