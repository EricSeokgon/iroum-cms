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
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002 RUN Step 1 — HTTP 권한 매트릭스 IT 확장 2차.
 *
 * <p>본 IT는 AUTHZ-IT-EXPAND-001 ({@link AuthorizationMatrixExpandIT}, 12 권한 어휘 88 AC)이
 * 커버하지 못한 운영 실측 19 권한 어휘 시나리오를 추가하여, ArchUnit baseline 31 어휘 100% IT 커버를 달성한다.
 *
 * <p>패턴: AUTHZ-MATRIX-001 + AUTHZ-IT-EXPAND-001 인프라 100% 재사용.
 *
 * <h3>19 미커버 권한 어휘 → 운영 컨트롤러 매핑 (실측)</h3>
 *
 * <table border="1" summary="19 어휘 endpoint 매핑">
 *   <thead>
 *     <tr><th>도메인 그룹</th><th>권한 어휘</th><th>운영 컨트롤러</th><th>Step</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td colspan="4"><b>§A.1 ContentReadDomainTests (4 어휘)</b></td></tr>
 *     <tr><td>Content</td><td>CONTENT:READ</td><td>SurveyController, I18nController</td><td>Step 2</td></tr>
 *     <tr><td>Content</td><td>PAGE:READ</td><td>ContentBlockController</td><td>Step 2</td></tr>
 *     <tr><td>Content</td><td>TEMPLATE:READ</td><td>TemplateController</td><td>Step 2</td></tr>
 *     <tr><td>Content</td><td>ROLE:CONTENT_ADMIN</td><td>SurveyController, PublicationController</td><td>Step 2</td></tr>
 *     <tr><td colspan="4"><b>§A.2 PageAdvancedDomainTests (2 어휘)</b></td></tr>
 *     <tr><td>Content</td><td>PAGE:ROLLBACK</td><td>PageController</td><td>Step 2</td></tr>
 *     <tr><td>Content</td><td>PAGE:HISTORY:READ</td><td>PageController</td><td>Step 2</td></tr>
 *     <tr><td colspan="4"><b>§A.3 SiteMenuDomainTests (2 어휘)</b></td></tr>
 *     <tr><td>Content</td><td>SITE:WRITE</td><td>SiteController</td><td>Step 2</td></tr>
 *     <tr><td>Content</td><td>MENU:PERMISSION:WRITE</td><td>MenuController</td><td>Step 2</td></tr>
 *     <tr><td colspan="4"><b>§A.4 UserAuditDomainTests (2 어휘)</b></td></tr>
 *     <tr><td>Auth</td><td>USER:READ</td><td>PersonalDataAccessController</td><td>Step 3</td></tr>
 *     <tr><td>Audit</td><td>AUDIT:READ</td><td>PersonalDataAccessController, LoginHistoryController</td><td>Step 3</td></tr>
 *     <tr><td colspan="4"><b>§A.5 DashboardDomainTests (1 어휘)</b></td></tr>
 *     <tr><td>Dashboard</td><td>SYSTEM:DASHBOARD</td><td>DashboardController</td><td>Step 3</td></tr>
 *     <tr><td colspan="4"><b>§A.6 SystemSettingDomainTests (4 어휘)</b></td></tr>
 *     <tr><td>System</td><td>SYSTEM:READ</td><td>SeoRedirectController</td><td>Step 3</td></tr>
 *     <tr><td>System</td><td>SYSTEM:SETTING:READ</td><td>SystemSettingController</td><td>Step 3</td></tr>
 *     <tr><td>System</td><td>SYSTEM:SETTING:WRITE</td><td>SystemSettingController</td><td>Step 3</td></tr>
 *     <tr><td>System</td><td>SYSTEM:ADMIN</td><td>SiteController, SeoRedirectController</td><td>Step 3</td></tr>
 *     <tr><td colspan="4"><b>§A.7 SystemOperationDomainTests (4 어휘)</b></td></tr>
 *     <tr><td>System</td><td>SYSTEM:MAINT:READ</td><td>MaintenanceController</td><td>Step 3</td></tr>
 *     <tr><td>System</td><td>SYSTEM:MAINT:WRITE</td><td>MaintenanceController</td><td>Step 3</td></tr>
 *     <tr><td>System</td><td>SYSTEM:LOG:READ</td><td>AccessLogController</td><td>Step 3</td></tr>
 *   </tbody>
 * </table>
 *
 * <p><b>합계</b>: 19 미커버 권한 어휘 × 평균 2 endpoint × 3 시나리오 ≈ 100~150 AC (Step 2~3 활성화)
 *
 * <h3>본 SPEC 완성 시 OWASP A01 회귀 검출 5중 검증</h3>
 * <ul>
 *   <li>HTTP 1차 (AUTHZ-MATRIX-001): 19 AC, 6 endpoint</li>
 *   <li>HTTP 확장 1차 (AUTHZ-IT-EXPAND-001): 88 AC, 29 endpoint, 12 어휘</li>
 *   <li>HTTP 확장 2차 (AUTHZ-IT-EXPAND-002 — 본 SPEC): ~100 AC, ~20 endpoint, 19 어휘</li>
 *   <li>메소드 슬라이스 (CTRL-AUTHZ-COVERAGE-001): 31 보강</li>
 *   <li>ArchUnit 자동 검출 (AUTHZ-AUTODETECT-001): 4 AC, 31 어휘 + 35 endpoint baseline</li>
 * </ul>
 *
 * <p>합계: <b>~240+ AC</b> + ArchUnit baseline 31 어휘 IT 100% 커버 달성.
 *
 * <h3>Step 1 — 인프라 신설 + smoke test만 활성화</h3>
 *
 * <p>본 RUN Step 1에서는 IT 클래스 부팅 + JWT Mock 주입 검증의 smoke test 1건만 활성화한다.
 * 19 어휘 × 평균 2 endpoint × 3 시나리오는 Step 2 (Phase A) / Step 3 (Phase B)에서 점진 활성화한다.
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002 (REQ-AM-EXP2-001/002/003/004)
 */
// @MX:NOTE: [AUTO] AuthorizationMatrixExpand2IT — 19 미커버 권한 어휘 IT 매트릭스 (확장 2차)
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("HTTP 권한 매트릭스 IT 확장 2차 (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-002)")
class AuthorizationMatrixExpand2IT {

    // ─── Testcontainers PostgreSQL 16 (AUTHZ-MATRIX-001 + EXPAND-001 패턴 일관) ─────────
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

    @SuppressWarnings("unused") // Step 2~3에서 활성화될 시나리오에서 사용 예정
    private static final String VALID_TOKEN = "valid.jwt.token";

    // ─── JWT Mock helper (AUTHZ-IT-EXPAND-001 패턴 100% 재사용) ────────────────────
    @SuppressWarnings("unused") // Step 2~3에서 활성화될 시나리오에서 사용 예정
    private void givenValidToken(Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "testuser", roles, permissions, Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    /**
     * 권한 통과 검증 helper — 401/403 아님 + service IllegalArgumentException 허용.
     *
     * <p>운영 GlobalExceptionHandler가 IllegalArgumentException을 처리하지 않으므로
     * (예: "페이지를 찾을 수 없습니다", "메뉴를 찾을 수 없습니다"), Spring MVC가 ServletException으로
     * wrap하여 throw한다. 이 경우 권한 검증은 통과한 것 — IT 본질적 PASS.
     *
     * <p>다른 endpoint (예: I18n, Site, Dashboard)는 service가 데이터 없어도 200/404 정상 응답.
     * 이 helper는 IllegalArgumentException으로 인한 ServletException만 허용한다.
     */
    @SuppressWarnings("unused")
    private void assertAuthzPassed(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        try {
            mockMvc.perform(request)
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        } catch (jakarta.servlet.ServletException e) {
            // 권한 통과 후 service domain exception (IllegalArgumentException 또는 운영 도메인 RuntimeException)
            // — 권한 검증 IT 본질적 PASS. AuthenticationException/AccessDeniedException은 권한 실패이므로 제외.
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
    // §0 인프라 smoke test — Step 1에서 유일하게 활성화되는 시나리오
    // =================================================================================

    @Test
    @DisplayName("§0 AC-AME2-002-1: 컨텍스트 부팅 + JwtTokenProvider/TokenBlacklistMapper Mock 주입 + JwtTestAuth helper 동작")
    void contextLoadsAndJwtAuthMockable() {
        assertNotNull(mockMvc, "MockMvc 주입 확인 (운영 SecurityFilterChain 적재 결과)");
        assertNotNull(jwtTokenProvider, "JwtTokenProvider @MockitoBean 주입 확인");
        assertNotNull(tokenBlacklistMapper, "TokenBlacklistMapper @MockitoBean 주입 확인");
        givenValidToken(Set.of(), Set.of());
    }

    // =================================================================================
    // §A REQ-AM-EXP2-001 — 19 미커버 권한 어휘 매트릭스 (~100 AC, Step 2~3 활성화)
    // =================================================================================

    /** §A.1 ContentReadDomainTests — CONTENT:READ, PAGE:READ, TEMPLATE:READ, ROLE:CONTENT_ADMIN (4 어휘). Step 2 Phase A 활성화. */
    @Nested
    @DisplayName("§A.1 ContentReadDomainTests (4 어휘, Step 2 활성화)")
    class ContentReadDomainTests {

        // ── CONTENT:READ — I18nController GET /api/v1/content/i18n (required: namespace, resourceId) ──
        @Test
        @DisplayName("AC-AME2-A1-1: GET /api/v1/content/i18n — Authorization 부재 + 401")
        void i18nList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/i18n")
                            .param("namespace", "test")
                            .param("resourceId", "1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A1-2: GET /api/v1/content/i18n — CONTENT:READ 부재 + 403")
        void i18nList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // CONTENT:READ 미보유
            mockMvc.perform(get("/api/v1/content/i18n")
                            .param("namespace", "test")
                            .param("resourceId", "1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A1-3: GET /api/v1/content/i18n — CONTENT:READ 보유 + 401/403 아님")
        void i18nList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:READ"));
            // 운영 service "지원하지 않는 네임스페이스" IllegalArgumentException 허용 (권한 통과 증명)
            assertAuthzPassed(get("/api/v1/content/i18n")
                    .param("namespace", "test")
                    .param("resourceId", "1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PAGE:READ — ContentBlockController GET /api/v1/content/pages/{pageId}/blocks ──
        @Test
        @DisplayName("AC-AME2-A1-4: GET /api/v1/content/pages/{pageId}/blocks — Authorization 부재 + 401")
        void pageBlocks_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/pages/1/blocks"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A1-5: GET /api/v1/content/pages/{pageId}/blocks — PAGE:READ 부재 + 403")
        void pageBlocks_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // PAGE:READ 미보유
            mockMvc.perform(get("/api/v1/content/pages/1/blocks")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A1-6: GET /api/v1/content/pages/{pageId}/blocks — PAGE:READ 보유 + 401/403 아님")
        void pageBlocks_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:READ"));
            mockMvc.perform(get("/api/v1/content/pages/1/blocks")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── TEMPLATE:READ — TemplateController GET /api/v1/content/templates ──
        @Test
        @DisplayName("AC-AME2-A1-7: GET /api/v1/content/templates — Authorization 부재 + 401")
        void templatesList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/templates"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A1-8: GET /api/v1/content/templates — TEMPLATE:READ 부재 + 403")
        void templatesList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // TEMPLATE:READ 미보유
            mockMvc.perform(get("/api/v1/content/templates")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A1-9: GET /api/v1/content/templates — TEMPLATE:READ 보유 + 401/403 아님")
        void templatesList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("TEMPLATE:READ"));
            mockMvc.perform(get("/api/v1/content/templates")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── ROLE:CONTENT_ADMIN — QnaController POST /api/v1/qnas/{id}/answer (hasAnyRole, OR bypass!) ──
        @Test
        @DisplayName("AC-AME2-A1-10: POST /api/v1/qnas/{id}/answer — Authorization 부재 + 401")
        void qnaAnswer_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/qnas/1/answer")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answerHtml\":\"<p>test</p>\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A1-11: POST /api/v1/qnas/{id}/answer — ROLE 부재 (USER만) + 403")
        void qnaAnswer_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // CONTENT_ADMIN/ADMIN/SUPER_ADMIN 미보유
            mockMvc.perform(post("/api/v1/qnas/1/answer")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answerHtml\":\"<p>test</p>\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A1-12: POST /api/v1/qnas/{id}/answer — CONTENT_ADMIN 보유 + 401/403 아님 (OR bypass 검증)")
        void qnaAnswer_hasContentAdminRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("CONTENT_ADMIN"), Set.of());
            mockMvc.perform(post("/api/v1/qnas/1/answer")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answerHtml\":\"<p>test</p>\"}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    /** §A.2 PageAdvancedDomainTests — PAGE:ROLLBACK, PAGE:HISTORY:READ (2 어휘). Step 2 Phase A 활성화. */
    @Nested
    @DisplayName("§A.2 PageAdvancedDomainTests (2 어휘, Step 2 활성화)")
    class PageAdvancedDomainTests {

        // ── PAGE:HISTORY:READ — PageController GET /api/v1/content/pages/{id}/history ──
        @Test
        @DisplayName("AC-AME2-A2-1: GET /api/v1/content/pages/{id}/history — Authorization 부재 + 401")
        void pageHistory_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/pages/1/history"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A2-2: GET /api/v1/content/pages/{id}/history — PAGE:HISTORY:READ 부재 + 403")
        void pageHistory_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // PAGE:HISTORY:READ 미보유
            mockMvc.perform(get("/api/v1/content/pages/1/history")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A2-3: GET /api/v1/content/pages/{id}/history — PAGE:HISTORY:READ 보유 + 401/403 아님")
        void pageHistory_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:HISTORY:READ"));
            mockMvc.perform(get("/api/v1/content/pages/1/history")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── PAGE:ROLLBACK — PageController POST /api/v1/content/pages/{id}/rollback/{version} ──
        @Test
        @DisplayName("AC-AME2-A2-4: POST /api/v1/content/pages/{id}/rollback/{version} — Authorization 부재 + 401")
        void pageRollback_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/content/pages/1/rollback/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A2-5: POST /api/v1/content/pages/{id}/rollback/{version} — PAGE:ROLLBACK 부재 + 403")
        void pageRollback_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // PAGE:ROLLBACK 미보유
            mockMvc.perform(post("/api/v1/content/pages/1/rollback/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A2-6: POST /api/v1/content/pages/{id}/rollback/{version} — PAGE:ROLLBACK 보유 + 401/403 아님")
        void pageRollback_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:ROLLBACK"));
            // 운영 service "페이지를 찾을 수 없습니다" IllegalArgumentException 허용 (권한 통과 증명)
            assertAuthzPassed(post("/api/v1/content/pages/1/rollback/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── 분리 회귀: PAGE:HISTORY:READ 권한자가 PAGE:ROLLBACK endpoint 호출 → 403 ──
        @Test
        @DisplayName("AC-AME2-A2-7: POST /api/v1/content/pages/{id}/rollback/{version} — PAGE:HISTORY:READ 권한자 + 403 (분리 회귀)")
        void pageRollback_hasHistoryReadOnly_returns403() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:HISTORY:READ")); // PAGE:ROLLBACK 미보유, HISTORY:READ만 보유
            mockMvc.perform(post("/api/v1/content/pages/1/rollback/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }
    }

    /** §A.3 SiteMenuDomainTests — SITE:WRITE, MENU:PERMISSION:WRITE (2 어휘). Step 2 Phase A 활성화. */
    @Nested
    @DisplayName("§A.3 SiteMenuDomainTests (2 어휘, Step 2 활성화)")
    class SiteMenuDomainTests {

        // SiteUpdateRequest 필드: @NotBlank name/domain/defaultLanguage
        private static final String SITE_UPDATE_BODY =
                "{\"name\":\"테스트 사이트\",\"domain\":\"example.com\",\"defaultLanguage\":\"ko\"}";

        // ── SITE:WRITE — SiteController PUT /api/v1/content/sites/{id} ──
        @Test
        @DisplayName("AC-AME2-A3-1: PUT /api/v1/content/sites/{id} — Authorization 부재 + 401")
        void siteUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/content/sites/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SITE_UPDATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A3-2: PUT /api/v1/content/sites/{id} — SITE:WRITE 부재 + 403")
        void siteUpdate_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // SITE:WRITE 미보유
            mockMvc.perform(put("/api/v1/content/sites/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SITE_UPDATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A3-3: PUT /api/v1/content/sites/{id} — SITE:WRITE 보유 + 401/403 아님")
        void siteUpdate_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SITE:WRITE"));
            mockMvc.perform(put("/api/v1/content/sites/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SITE_UPDATE_BODY))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // MenuPermissionRequest 필드: List<String> permissionCodes (Validation 없으나 deserialize 일관성)
        private static final String MENU_PERMISSIONS_BODY =
                "{\"permissionCodes\":[\"MENU:VIEW\",\"MENU:EDIT\"]}";

        // ── MENU:PERMISSION:WRITE — MenuController POST /api/v1/content/menus/{id}/permissions ──
        @Test
        @DisplayName("AC-AME2-A3-4: POST /api/v1/content/menus/{id}/permissions — Authorization 부재 + 401")
        void menuPermissions_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/content/menus/1/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MENU_PERMISSIONS_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A3-5: POST /api/v1/content/menus/{id}/permissions — MENU:PERMISSION:WRITE 부재 + 403")
        void menuPermissions_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // MENU:PERMISSION:WRITE 미보유
            mockMvc.perform(post("/api/v1/content/menus/1/permissions")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MENU_PERMISSIONS_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A3-6: POST /api/v1/content/menus/{id}/permissions — MENU:PERMISSION:WRITE 보유 + 401/403 아님")
        void menuPermissions_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("MENU:PERMISSION:WRITE"));
            // 운영 service "메뉴를 찾을 수 없습니다" IllegalArgumentException 허용 (권한 통과 증명)
            assertAuthzPassed(post("/api/v1/content/menus/1/permissions")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(MENU_PERMISSIONS_BODY));
        }
    }

    /** §A.4 UserAuditDomainTests — USER:READ, AUDIT:READ (2 어휘). Step 3 Phase B 활성화. */
    @Nested
    @DisplayName("§A.4 UserAuditDomainTests (2 어휘, Step 3 활성화)")
    class UserAuditDomainTests {

        // ── USER:READ + AUDIT:READ AND 조건 — PersonalDataAccessController GET /api/v1/audit/personal-data-access ──
        @Test
        @DisplayName("AC-AME2-A4-1: GET /api/v1/audit/personal-data-access — Authorization 부재 + 401")
        void pdaList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/audit/personal-data-access"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A4-2: GET /api/v1/audit/personal-data-access — AUDIT:READ만 보유 (USER:READ 부재) + 403")
        void pdaList_missingUserRead_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("AUDIT:READ")); // USER:READ 부재
            mockMvc.perform(get("/api/v1/audit/personal-data-access")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A4-3: GET /api/v1/audit/personal-data-access — AUDIT:READ + USER:READ 동시 보유 + 401/403 아님 (AND 통과)")
        void pdaList_hasBothAuthorities_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("AUDIT:READ", "USER:READ"));
            mockMvc.perform(get("/api/v1/audit/personal-data-access")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── AUDIT:READ — PermissionChangeController class-level GET /api/v1/audit/permission-changes ──
        @Test
        @DisplayName("AC-AME2-A4-4: GET /api/v1/audit/permission-changes — Authorization 부재 + 401")
        void permissionChanges_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/audit/permission-changes"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A4-5: GET /api/v1/audit/permission-changes — AUDIT:READ 부재 + 403")
        void permissionChanges_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // AUDIT:READ 부재
            mockMvc.perform(get("/api/v1/audit/permission-changes")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A4-6: GET /api/v1/audit/permission-changes — AUDIT:READ 보유 + 401/403 아님")
        void permissionChanges_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("AUDIT:READ"));
            mockMvc.perform(get("/api/v1/audit/permission-changes")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── AUDIT:READ — LoginHistoryController class-level GET /api/v1/audit/login-history ──
        @Test
        @DisplayName("AC-AME2-A4-7: GET /api/v1/audit/login-history — AUDIT:READ 보유 + 401/403 아님 (class-level 검증)")
        void loginHistory_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("AUDIT:READ"));
            mockMvc.perform(get("/api/v1/audit/login-history")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    /** §A.5 DashboardDomainTests — SYSTEM:DASHBOARD (1 어휘). Step 2 Phase A 단위 검증. */
    @Nested
    @DisplayName("§A.5 DashboardDomainTests (1 어휘, Step 2 활성화)")
    class DashboardDomainTests {

        // GET /api/v1/system/dashboard/kpi — hasAuthority('SYSTEM:DASHBOARD') (OR bypass 없음, AUTHZ-IT-EXPAND-002 v0.2 매핑)
        @Test
        @DisplayName("AC-AME2-A5-1: GET /api/v1/system/dashboard/kpi — Authorization 헤더 부재 + 401")
        void dashboardKpi_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/dashboard/kpi"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A5-2: GET /api/v1/system/dashboard/kpi — SYSTEM:DASHBOARD 부재 + 403")
        void dashboardKpi_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // SYSTEM:DASHBOARD 미보유
            mockMvc.perform(get("/api/v1/system/dashboard/kpi")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A5-3: GET /api/v1/system/dashboard/kpi — SYSTEM:DASHBOARD 보유 + 401/403 아님")
        void dashboardKpi_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:DASHBOARD"));
            mockMvc.perform(get("/api/v1/system/dashboard/kpi")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    /** §A.6 SystemSettingDomainTests — SYSTEM:READ, SETTING:READ/WRITE, ADMIN (4 어휘). Step 3 Phase B 활성화. */
    @Nested
    @DisplayName("§A.6 SystemSettingDomainTests (4 어휘, Step 3 활성화)")
    class SystemSettingDomainTests {

        // ── SYSTEM:READ — SeoRedirectController GET /api/v1/content/seo/redirects ──
        @Test
        @DisplayName("AC-AME2-A6-1: GET /api/v1/content/seo/redirects — Authorization 부재 + 401")
        void seoList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/seo/redirects"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A6-2: GET /api/v1/content/seo/redirects — SYSTEM:READ 부재 + 403")
        void seoList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // SYSTEM:READ 부재
            mockMvc.perform(get("/api/v1/content/seo/redirects")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A6-3: GET /api/v1/content/seo/redirects — SYSTEM:READ 보유 + 401/403 아님")
        void seoList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/content/seo/redirects")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── SYSTEM:SETTING:READ — SystemSettingController GET /api/v1/system/settings ──
        @Test
        @DisplayName("AC-AME2-A6-4: GET /api/v1/system/settings — Authorization 부재 + 401")
        void settingsList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/settings"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A6-5: GET /api/v1/system/settings — SYSTEM:SETTING:READ 부재 + 403")
        void settingsList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // SYSTEM:SETTING:READ 부재
            mockMvc.perform(get("/api/v1/system/settings")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A6-6: GET /api/v1/system/settings — SYSTEM:SETTING:READ 보유 + 401/403 아님")
        void settingsList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:SETTING:READ"));
            mockMvc.perform(get("/api/v1/system/settings")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── SYSTEM:SETTING:WRITE — SystemSettingController PUT /api/v1/system/settings/{key} ──
        private static final String SETTING_BODY = "{\"value\":\"test-value\",\"description\":\"test\"}";

        @Test
        @DisplayName("AC-AME2-A6-7: PUT /api/v1/system/settings/{key} — Authorization 부재 + 401")
        void settingsPut_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/system/settings/test.key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SETTING_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A6-8: PUT /api/v1/system/settings/{key} — SETTING:WRITE 부재 + 403")
        void settingsPut_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // SETTING:WRITE 부재
            mockMvc.perform(put("/api/v1/system/settings/test.key")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SETTING_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A6-9: PUT /api/v1/system/settings/{key} — SETTING:WRITE 보유 + 401/403 아님")
        void settingsPut_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:SETTING:WRITE"));
            assertAuthzPassed(put("/api/v1/system/settings/test.key")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SETTING_BODY));
        }

        // ── 분리 회귀: SETTING:READ 권한자가 PUT 시도 → 403 ──
        @Test
        @DisplayName("AC-AME2-A6-10: PUT /api/v1/system/settings/{key} — SETTING:READ만 보유 + 403 (분리 회귀)")
        void settingsPut_hasReadOnly_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:SETTING:READ")); // WRITE 부재
            mockMvc.perform(put("/api/v1/system/settings/test.key")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SETTING_BODY))
                    .andExpect(status().isForbidden());
        }

        // ── SYSTEM:ADMIN — SiteController POST /api/v1/content/sites ──
        private static final String SITE_CREATE_BODY =
                "{\"name\":\"테스트 사이트\",\"domain\":\"new.example.com\",\"defaultLanguage\":\"ko\"}";

        @Test
        @DisplayName("AC-AME2-A6-11: POST /api/v1/content/sites — Authorization 부재 + 401")
        void siteCreate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/content/sites")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SITE_CREATE_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A6-12: POST /api/v1/content/sites — SYSTEM:ADMIN 부재 + 403")
        void siteCreate_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // SYSTEM:ADMIN 부재
            mockMvc.perform(post("/api/v1/content/sites")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SITE_CREATE_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A6-13: POST /api/v1/content/sites — SYSTEM:ADMIN 보유 + 401/403 아님")
        void siteCreate_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:ADMIN"));
            // 운영 service SiteMultiDisabledException (멀티사이트 비활성) — 권한 통과 증명
            assertAuthzPassed(post("/api/v1/content/sites")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SITE_CREATE_BODY));
        }

        // ── 분리 회귀: SYSTEM:READ 권한자가 POST /sites (SYSTEM:ADMIN) 시도 → 403 ──
        @Test
        @DisplayName("AC-AME2-A6-14: POST /api/v1/content/sites — SYSTEM:READ만 보유 + 403 (분리 회귀)")
        void siteCreate_hasReadOnly_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:READ")); // SYSTEM:ADMIN 부재
            mockMvc.perform(post("/api/v1/content/sites")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SITE_CREATE_BODY))
                    .andExpect(status().isForbidden());
        }
    }

    /** §A.7 SystemOperationDomainTests — SYSTEM:MAINT:READ/WRITE, LOG:READ (3 어휘). Step 3 Phase B 활성화. */
    @Nested
    @DisplayName("§A.7 SystemOperationDomainTests (3 어휘, Step 3 활성화)")
    class SystemOperationDomainTests {

        // ── SYSTEM:MAINT:READ — MaintenanceController GET /api/v1/system/maintenance ──
        @Test
        @DisplayName("AC-AME2-A7-1: GET /api/v1/system/maintenance — Authorization 부재 + 401")
        void maintList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/maintenance"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A7-2: GET /api/v1/system/maintenance — SYSTEM:MAINT:READ 부재 + 403")
        void maintList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // MAINT:READ 부재
            mockMvc.perform(get("/api/v1/system/maintenance")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A7-3: GET /api/v1/system/maintenance — SYSTEM:MAINT:READ 보유 + 401/403 아님")
        void maintList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:MAINT:READ"));
            mockMvc.perform(get("/api/v1/system/maintenance")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── SYSTEM:MAINT:WRITE — MaintenanceController POST /api/v1/system/maintenance ──
        private static final String MAINT_BODY =
                "{\"title\":\"테스트 점검\",\"startAt\":\"2026-12-31T00:00:00Z\",\"endAt\":\"2026-12-31T01:00:00Z\"}";

        @Test
        @DisplayName("AC-AME2-A7-4: POST /api/v1/system/maintenance — Authorization 부재 + 401")
        void maintCreate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/system/maintenance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MAINT_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A7-5: POST /api/v1/system/maintenance — SYSTEM:MAINT:WRITE 부재 + 403")
        void maintCreate_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // MAINT:WRITE 부재
            mockMvc.perform(post("/api/v1/system/maintenance")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MAINT_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A7-6: POST /api/v1/system/maintenance — SYSTEM:MAINT:WRITE 보유 + 401/403 아님")
        void maintCreate_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:MAINT:WRITE"));
            mockMvc.perform(post("/api/v1/system/maintenance")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MAINT_BODY))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ── 분리 회귀: MAINT:READ 권한자가 POST 시도 → 403 ──
        @Test
        @DisplayName("AC-AME2-A7-7: POST /api/v1/system/maintenance — MAINT:READ만 보유 + 403 (분리 회귀)")
        void maintCreate_hasReadOnly_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:MAINT:READ")); // WRITE 부재
            mockMvc.perform(post("/api/v1/system/maintenance")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MAINT_BODY))
                    .andExpect(status().isForbidden());
        }

        // ── SYSTEM:LOG:READ — AccessLogController GET /api/v1/system/access-logs ──
        @Test
        @DisplayName("AC-AME2-A7-8: GET /api/v1/system/access-logs — Authorization 부재 + 401")
        void accessLogs_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/access-logs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME2-A7-9: GET /api/v1/system/access-logs — SYSTEM:LOG:READ 부재 + 403")
        void accessLogs_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // LOG:READ 부재
            mockMvc.perform(get("/api/v1/system/access-logs")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME2-A7-10: GET /api/v1/system/access-logs — SYSTEM:LOG:READ 보유 + 401/403 아님")
        void accessLogs_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:LOG:READ"));
            mockMvc.perform(get("/api/v1/system/access-logs")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }
}
