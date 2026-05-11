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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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

    /** §A.1 ContentReadDomainTests — CONTENT:READ, PAGE:READ, TEMPLATE:READ, ROLE:CONTENT_ADMIN (4 어휘). */
    @Nested
    @DisplayName("§A.1 ContentReadDomainTests (4 어휘, Step 2 활성화)")
    class ContentReadDomainTests {
        @Test
        @Disabled("Step 2 (Phase A)에서 활성화 예정 — CONTENT:READ/PAGE:READ/TEMPLATE:READ/CONTENT_ADMIN 매트릭스")
        @DisplayName("§A.1 placeholder: Step 2 활성화 대기")
        void contentReadDomain_placeholder_step2() {
            // 4 어휘 × 평균 2 endpoint × 3 시나리오 ≈ 24 AC
        }
    }

    /** §A.2 PageAdvancedDomainTests — PAGE:ROLLBACK, PAGE:HISTORY:READ (2 어휘). */
    @Nested
    @DisplayName("§A.2 PageAdvancedDomainTests (2 어휘, Step 2 활성화)")
    class PageAdvancedDomainTests {
        @Test
        @Disabled("Step 2 (Phase A)에서 활성화 예정 — PAGE:ROLLBACK/PAGE:HISTORY:READ 분리 회귀 매트릭스")
        @DisplayName("§A.2 placeholder: Step 2 활성화 대기")
        void pageAdvancedDomain_placeholder_step2() {
            // PAGE:ROLLBACK vs PAGE:HISTORY:READ vs 기존 PAGE:WRITE/PUBLISH 분리 회귀 검증
        }
    }

    /** §A.3 SiteMenuDomainTests — SITE:WRITE, MENU:PERMISSION:WRITE (2 어휘). */
    @Nested
    @DisplayName("§A.3 SiteMenuDomainTests (2 어휘, Step 2 활성화)")
    class SiteMenuDomainTests {
        @Test
        @Disabled("Step 2 (Phase A)에서 활성화 예정 — SITE:WRITE/MENU:PERMISSION:WRITE 매트릭스")
        @DisplayName("§A.3 placeholder: Step 2 활성화 대기")
        void siteMenuDomain_placeholder_step2() {
            // MENU:PERMISSION:WRITE vs 기존 MENU:WRITE 분리 회귀 검증
        }
    }

    /** §A.4 UserAuditDomainTests — USER:READ, AUDIT:READ (2 어휘). */
    @Nested
    @DisplayName("§A.4 UserAuditDomainTests (2 어휘, Step 3 활성화)")
    class UserAuditDomainTests {
        @Test
        @Disabled("Step 3 (Phase B)에서 활성화 예정 — USER:READ/AUDIT:READ 매트릭스")
        @DisplayName("§A.4 placeholder: Step 3 활성화 대기")
        void userAuditDomain_placeholder_step3() {
            // PersonalDataAccessController, LoginHistoryController endpoint
        }
    }

    /** §A.5 DashboardDomainTests — SYSTEM:DASHBOARD (1 어휘). */
    @Nested
    @DisplayName("§A.5 DashboardDomainTests (1 어휘, Step 3 활성화)")
    class DashboardDomainTests {
        @Test
        @Disabled("Step 3 (Phase B)에서 활성화 예정 — SYSTEM:DASHBOARD 매트릭스")
        @DisplayName("§A.5 placeholder: Step 3 활성화 대기")
        void dashboardDomain_placeholder_step3() {
            // DashboardController endpoint
        }
    }

    /** §A.6 SystemSettingDomainTests — SYSTEM:READ, SETTING:READ/WRITE, ADMIN (4 어휘). */
    @Nested
    @DisplayName("§A.6 SystemSettingDomainTests (4 어휘, Step 3 활성화)")
    class SystemSettingDomainTests {
        @Test
        @Disabled("Step 3 (Phase B)에서 활성화 예정 — SYSTEM:READ/SETTING:READ/WRITE/ADMIN 매트릭스")
        @DisplayName("§A.6 placeholder: Step 3 활성화 대기")
        void systemSettingDomain_placeholder_step3() {
            // SYSTEM:SETTING:READ vs WRITE 분리 회귀 + SYSTEM:READ vs ADMIN 분리 회귀 검증
        }
    }

    /** §A.7 SystemOperationDomainTests — SYSTEM:MAINT:READ/WRITE, LOG:READ (3 어휘). */
    @Nested
    @DisplayName("§A.7 SystemOperationDomainTests (3 어휘, Step 3 활성화)")
    class SystemOperationDomainTests {
        @Test
        @Disabled("Step 3 (Phase B)에서 활성화 예정 — SYSTEM:MAINT:READ/WRITE/LOG:READ 매트릭스")
        @DisplayName("§A.7 placeholder: Step 3 활성화 대기")
        void systemOperationDomain_placeholder_step3() {
            // SYSTEM:MAINT:READ vs WRITE 분리 회귀 + LOG:READ 매트릭스
        }
    }
}
