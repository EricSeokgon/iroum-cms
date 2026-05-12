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

    /** §A.1 OrganizationDomainTests — OrganizationController 7개 미커버 endpoint (Phase A). */
    @Nested
    @DisplayName("§A.1 OrganizationDomainTests (7 미커버, Step 3 Phase A 활성화)")
    class OrganizationDomainTests {
        @Test
        @Disabled("Step 3 (Phase A)에서 활성화 예정 — Organization GET/PATCH/DELETE/tree/move 등")
        @DisplayName("§A.1 placeholder: Step 3 활성화 대기")
        void organizationDomain_placeholder_step3() {
            // GET /organizations, GET /organizations/tree, PATCH/DELETE /organizations/{id} 등
        }
    }

    /** §A.2 UserDomainTests — UserController 6개 미커버 endpoint (Phase A). */
    @Nested
    @DisplayName("§A.2 UserDomainTests (6 미커버, Step 3 Phase A 활성화)")
    class UserDomainTests {
        @Test
        @Disabled("Step 3 (Phase A)에서 활성화 예정 — User GET/PATCH/DELETE 등")
        @DisplayName("§A.2 placeholder: Step 3 활성화 대기")
        void userDomain_placeholder_step3() {
            // GET /users, GET /users/{id}, PATCH /users/{id}, DELETE /users/{id} 등
        }
    }

    /** §A.3 CodeDomainTests — CodeController + CodeGroupController 미커버 endpoint (Phase A). */
    @Nested
    @DisplayName("§A.3 CodeDomainTests (Code 4 + CodeGroup 4 미커버, Step 3 Phase A 활성화)")
    class CodeDomainTests {
        @Test
        @Disabled("Step 3 (Phase A)에서 활성화 예정 — Code GET/{id}/bulk + CodeGroup GET/PUT/DELETE")
        @DisplayName("§A.3 placeholder: Step 3 활성화 대기")
        void codeDomain_placeholder_step3() {
            // Code: bulk, get(id), update, delete + CodeGroup: list, get, update, delete
        }
    }

    /** §A.4 MenuMaintenanceDomainTests — MenuController + MaintenanceController 미커버 (Phase B). */
    @Nested
    @DisplayName("§A.4 MenuMaintenanceDomainTests (Menu 4 + Maintenance 2 미커버, Step 4 Phase B 활성화)")
    class MenuMaintenanceDomainTests {
        @Test
        @Disabled("Step 4 (Phase B)에서 활성화 예정 — Menu PUT + Maintenance PUT/DELETE 등")
        @DisplayName("§A.4 placeholder: Step 4 활성화 대기")
        void menuMaintenanceDomain_placeholder_step4() {
            // Menu PUT /{id} + Maintenance complete/cancel 등
        }
    }

    /** §A.5 DashboardWidgetSettingDomainTests — DashboardWidget + SystemSetting 미커버 (Phase B). */
    @Nested
    @DisplayName("§A.5 DashboardWidgetSettingDomainTests (Widget 2 + Setting 2 미커버, Step 4 Phase B 활성화)")
    class DashboardWidgetSettingDomainTests {
        @Test
        @Disabled("Step 4 (Phase B)에서 활성화 예정 — Widget GET/DELETE + Setting DELETE 등")
        @DisplayName("§A.5 placeholder: Step 4 활성화 대기")
        void widgetSettingDomain_placeholder_step4() {
            // Widget list, get, delete + Setting delete
        }
    }

    /** §A.6 BannerSiteI18nDomainTests — Banner + Site + I18n 미커버 (Phase B). */
    @Nested
    @DisplayName("§A.6 BannerSiteI18nDomainTests (Banner 2 + Site 1 + I18n 1 미커버, Step 4 Phase B 활성화)")
    class BannerSiteI18nDomainTests {
        @Test
        @Disabled("Step 4 (Phase B)에서 활성화 예정 — Banner GET/DELETE + Site GET + I18n PUT 등")
        @DisplayName("§A.6 placeholder: Step 4 활성화 대기")
        void bannerSiteI18nDomain_placeholder_step4() {
            // Banner list/delete + Site get + I18n update
        }
    }

    /** §A.7 SearchPermissionDomainTests — Search + Synonym + Permission 미커버 (Phase C). */
    @Nested
    @DisplayName("§A.7 SearchPermissionDomainTests (Search 1 + Synonym 1 + Permission 1 미커버, Step 5 Phase C 활성화)")
    class SearchPermissionDomainTests {
        @Test
        @Disabled("Step 5 (Phase C)에서 활성화 예정 — Search reindex + Synonym CRUD + Permission CRUD")
        @DisplayName("§A.7 placeholder: Step 5 활성화 대기")
        void searchPermissionDomain_placeholder_step5() {
            // Search reindex + Synonym update + Permission update
        }
    }

    /** §A.8 GovernanceStatsDomainTests — Governance + Stats 미커버 (Phase C). */
    @Nested
    @DisplayName("§A.8 GovernanceStatsDomainTests (Governance 5 + Stats 3 미커버, Step 5 Phase C 활성화)")
    class GovernanceStatsDomainTests {
        @Test
        @Disabled("Step 5 (Phase C)에서 활성화 예정 — Governance/Stats 잔여 endpoint")
        @DisplayName("§A.8 placeholder: Step 5 활성화 대기")
        void governanceStatsDomain_placeholder_step5() {
            // Governance audit + Stats trend 잔여
        }
    }
}
