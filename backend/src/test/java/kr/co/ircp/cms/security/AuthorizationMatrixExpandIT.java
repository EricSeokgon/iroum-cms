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
 * SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 RUN — HTTP 권한 매트릭스 IT 확장.
 *
 * <p>본 IT는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 v0.2 ({@link AuthorizationMatrixIT})와 검증
 * 레이어가 동일하나({@code @SpringBootTest}), endpoint 커버리지를 6 → 30으로 확장하여
 * 운영 {@code @PreAuthorize} 12 권한 어휘 모두 회귀 검출을 보장한다.
 *
 * <p>메소드 레벨 슬라이스 회귀({@code @WebMvcTest})는 SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001
 * 영역으로 본 IT와 직교한다.
 *
 * <p>인프라 패턴 — AUTHZ-MATRIX-001 100% 재사용:
 * <ul>
 *   <li>{@code @SpringBootTest(MOCK)} + {@code @AutoConfigureMockMvc} + {@code @Testcontainers} PostgreSQL 16</li>
 *   <li>{@code @MockitoBean JwtTokenProvider/TokenBlacklistMapper} (DB 토큰 저장 우회)</li>
 *   <li>PII 더미 키 ({@code pii.keyvault.keys.v1} AES-256 + {@code pii.keyvault.hmac-key} HMAC) — SPEC-PII-001 패턴</li>
 *   <li>{@code givenValidToken(roles, permissions)} JWT Mock helper — 권한 시뮬레이션 표준화</li>
 * </ul>
 *
 * <h3>30 endpoint × 12 권한 어휘 매트릭스 (acceptance §A 분포 일관)</h3>
 *
 * <table border="1" summary="30 endpoint × 권한 어휘 매핑">
 *   <thead>
 *     <tr><th>도메인 그룹</th><th>HTTP</th><th>경로</th><th>운영 @PreAuthorize 어휘</th><th>커버 어휘</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr><td colspan="5"><b>§A.1 ContentDomainTests (7 endpoint)</b></td></tr>
 *     <tr><td>Content</td><td>POST</td><td>/api/v1/content/popups</td><td>hasAuthority('CONTENT:WRITE')</td><td>CONTENT:WRITE (다른 컨트롤러)</td></tr>
 *     <tr><td>Content</td><td>PUT</td><td>/api/v1/content/pages/{id}</td><td>hasAuthority('PAGE:WRITE')</td><td>PAGE:WRITE</td></tr>
 *     <tr><td>Content</td><td>POST</td><td>/api/v1/content/pages/{id}/publish</td><td>hasAuthority('PAGE:PUBLISH')</td><td>PAGE:PUBLISH</td></tr>
 *     <tr><td>Content</td><td>POST</td><td>/api/v1/content/pages/{id}/schedule</td><td>hasAuthority('PAGE:PUBLISH')</td><td>PAGE:PUBLISH</td></tr>
 *     <tr><td>Content</td><td>POST</td><td>/api/v1/content/pages/{id}/retract</td><td>hasAuthority('PAGE:PUBLISH')</td><td>PAGE:PUBLISH</td></tr>
 *     <tr><td>Content</td><td>POST</td><td>/api/v1/content/templates</td><td>hasAuthority('TEMPLATE:WRITE')</td><td>TEMPLATE:WRITE</td></tr>
 *     <tr><td>Content</td><td>PUT</td><td>/api/v1/content/templates/{id}</td><td>hasAuthority('TEMPLATE:WRITE')</td><td>TEMPLATE:WRITE</td></tr>
 *     <tr><td colspan="5"><b>§A.2 BlockDomainTests (2 endpoint)</b></td></tr>
 *     <tr><td>Block</td><td>POST</td><td>/api/v1/content/pages/{pageId}/blocks</td><td>hasAuthority('BLOCK:WRITE')</td><td>BLOCK:WRITE</td></tr>
 *     <tr><td>Block</td><td>PUT</td><td>/api/v1/content/pages/{pageId}/blocks/{blockId}</td><td>hasAuthority('BLOCK:WRITE')</td><td>BLOCK:WRITE</td></tr>
 *     <tr><td colspan="5"><b>§A.3 DashboardDomainTests (3 endpoint)</b></td></tr>
 *     <tr><td>Dashboard</td><td>POST</td><td>/api/v1/dashboard/widgets</td><td>hasRole('SUPER_ADMIN')</td><td>SUPER_ADMIN (다른 컨트롤러)</td></tr>
 *     <tr><td>Dashboard</td><td>PUT</td><td>/api/v1/dashboard/widgets/{id}</td><td>hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')</td><td>hasAnyRole (다른 컨트롤러)</td></tr>
 *     <tr><td>Dashboard</td><td>GET</td><td>/api/v1/system/stats/trend</td><td>hasAuthority('SYSTEM:STATS')</td><td>SYSTEM:STATS</td></tr>
 *     <tr><td colspan="5"><b>§A.4 AuthDomainTests (4 endpoint, isAuthenticated 403 N/A)</b></td></tr>
 *     <tr><td>Auth</td><td>POST</td><td>/api/v1/users/{id}/force-logout</td><td>hasRole('SUPER_ADMIN')</td><td>SUPER_ADMIN</td></tr>
 *     <tr><td>Auth</td><td>POST</td><td>/api/v1/organizations</td><td>hasRole('SUPER_ADMIN')</td><td>SUPER_ADMIN</td></tr>
 *     <tr><td>Auth</td><td>GET</td><td>/api/v1/qnas</td><td>isAuthenticated()</td><td>isAuthenticated (401/200만)</td></tr>
 *     <tr><td>Auth</td><td>POST</td><td>/api/v1/qnas</td><td>isAuthenticated()</td><td>isAuthenticated (401/200만)</td></tr>
 *     <tr><td colspan="5"><b>§A.5 SystemDomainTests (5 endpoint)</b></td></tr>
 *     <tr><td>System</td><td>GET</td><td>/api/v1/system/codes</td><td>hasAuthority('SYSTEM:CODE:READ')</td><td>SYSTEM:CODE:READ</td></tr>
 *     <tr><td>System</td><td>GET</td><td>/api/v1/system/code-groups</td><td>hasAuthority('SYSTEM:CODE:READ')</td><td>SYSTEM:CODE:READ</td></tr>
 *     <tr><td>System</td><td>POST</td><td>/api/v1/system/codes</td><td>hasAuthority('SYSTEM:CODE:WRITE')</td><td>SYSTEM:CODE:WRITE</td></tr>
 *     <tr><td>System</td><td>PUT</td><td>/api/v1/system/codes/{id}</td><td>hasAuthority('SYSTEM:CODE:WRITE')</td><td>SYSTEM:CODE:WRITE</td></tr>
 *     <tr><td>System</td><td>POST</td><td>/api/v1/system/code-groups</td><td>hasAuthority('SYSTEM:CODE:WRITE')</td><td>SYSTEM:CODE:WRITE</td></tr>
 *     <tr><td colspan="5"><b>§A.6 GovernanceDomainTests (3 endpoint)</b></td></tr>
 *     <tr><td>Governance</td><td>GET</td><td>/api/v1/governance/quality-rules</td><td>hasRole('ADMIN') (클래스 레벨)</td><td>ADMIN</td></tr>
 *     <tr><td>Governance</td><td>POST</td><td>/api/v1/governance/quality-rules</td><td>hasRole('ADMIN') (클래스 레벨)</td><td>ADMIN</td></tr>
 *     <tr><td>Governance</td><td>POST</td><td>/api/v1/governance/recovery-drills</td><td>hasRole('ADMIN') (클래스 레벨)</td><td>ADMIN</td></tr>
 *     <tr><td colspan="5"><b>§A.7 BoardMenuDomainTests (5 endpoint)</b></td></tr>
 *     <tr><td>Menu</td><td>POST</td><td>/api/v1/content/menus</td><td>hasAuthority('MENU:WRITE')</td><td>MENU:WRITE</td></tr>
 *     <tr><td>Menu</td><td>PATCH</td><td>/api/v1/content/menus/{id}/order</td><td>hasAuthority('MENU:WRITE')</td><td>MENU:WRITE</td></tr>
 *     <tr><td>Menu</td><td>DELETE</td><td>/api/v1/content/menus/{id}</td><td>hasAuthority('MENU:WRITE')</td><td>MENU:WRITE</td></tr>
 *     <tr><td>Board</td><td>POST</td><td>/api/v1/boards</td><td>hasRole('ADMIN')</td><td>ADMIN (다른 컨트롤러 보강)</td></tr>
 *     <tr><td>Board</td><td>PUT</td><td>/api/v1/boards/{id}</td><td>hasRole('ADMIN')</td><td>ADMIN (다른 컨트롤러 보강)</td></tr>
 *   </tbody>
 * </table>
 *
 * <p><b>합계</b>: 7 + 2 + 3 + 4 + 5 + 3 + 5 = <b>29 endpoint</b> (acceptance §A 분포 일관, ~30 endpoint 목표 충족).
 *
 * <p><b>권한 어휘 12종 커버 검증</b>:
 * <ol>
 *   <li>{@code hasRole('SUPER_ADMIN')} — Auth(2) + Dashboard(1) = 3 endpoint</li>
 *   <li>{@code hasRole('ADMIN')} — Governance(3) + Board(2) = 5 endpoint</li>
 *   <li>{@code hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')} — Dashboard(1) = 1 endpoint</li>
 *   <li>{@code hasAuthority('CONTENT:WRITE')} — Content(1, popup) = 1 endpoint (다른 컨트롤러)</li>
 *   <li>{@code hasAuthority('PAGE:WRITE')} — Content(1) = 1 endpoint</li>
 *   <li>{@code hasAuthority('PAGE:PUBLISH')} — Content(3) = 3 endpoint</li>
 *   <li>{@code hasAuthority('SYSTEM:CODE:READ')} — System(2) = 2 endpoint</li>
 *   <li>{@code hasAuthority('SYSTEM:CODE:WRITE')} — System(3) = 3 endpoint</li>
 *   <li>{@code hasAuthority('SYSTEM:STATS')} — Dashboard(1) = 1 endpoint</li>
 *   <li>{@code hasAuthority('MENU:WRITE')} — BoardMenu(3) = 3 endpoint</li>
 *   <li>{@code hasAuthority('BLOCK:WRITE')} — Block(2) = 2 endpoint</li>
 *   <li>{@code hasAuthority('TEMPLATE:WRITE')} — Content(2) = 2 endpoint</li>
 *   <li>{@code isAuthenticated()} — Auth(2) = 2 endpoint (401/200만, 403 N/A)</li>
 * </ol>
 *
 * <p><b>AUTHZ-MATRIX-001 6 endpoint와 중복 0건 (검증)</b>:
 * <ul>
 *   <li>제외: POST /api/v1/content/banners, PUT /api/v1/content/banners/{id} (Banner 컨트롤러)</li>
 *   <li>제외: POST /api/v1/content/pages (Page POST — 본 IT는 Page PUT/publish/schedule/retract로 다른 endpoint 보강)</li>
 *   <li>제외: POST /api/v1/dashboard/cache/invalidate (CacheAdmin — 본 IT는 DashboardWidget으로 다른 컨트롤러 보강)</li>
 *   <li>제외: POST /api/v1/users (User register — 본 IT는 force-logout으로 다른 endpoint 보강)</li>
 *   <li>제외: GET /api/v1/governance/retention-policies (Retention — 본 IT는 quality-rules/recovery-drills로 다른 컨트롤러 보강)</li>
 * </ul>
 *
 * <h3>Step 1 — 신설 + smoke test 1건만 활성화</h3>
 *
 * <p>본 RUN Step 1에서는 IT 클래스 부팅 + JWT Mock 주입 검증의 smoke test 1건만 활성화한다.
 * 30 endpoint × 3 시나리오 ≈ 90 AC + 12 권한 어휘 회귀 검증 12 AC는 Step 2~4에서 점진 활성화한다:
 * <ul>
 *   <li>Step 2 (Phase A): 권한 어휘 1~6 (SUPER_ADMIN/ADMIN/hasAnyRole/CONTENT:WRITE/PAGE:WRITE/PAGE:PUBLISH/SYSTEM:CODE:READ)</li>
 *   <li>Step 3 (Phase B): 권한 어휘 7~12 (SYSTEM:CODE:WRITE/SYSTEM:STATS/MENU:WRITE/BLOCK:WRITE/TEMPLATE:WRITE/isAuthenticated)</li>
 *   <li>Step 4: 회귀 검증 + README 갱신</li>
 * </ul>
 *
 * <h3>신규 endpoint 추가 시 수동 갱신 절차 (D3 채택)</h3>
 *
 * <p>신규 {@code @PreAuthorize} 어노테이션이 운영 컨트롤러에 추가될 때:
 * <ol>
 *   <li>해당 권한 어휘가 위 12종 매트릭스 중 어느 것에 해당하는지 식별</li>
 *   <li>해당 도메인 {@code @Nested} 그룹에 401/403/200 3 시나리오 추가
 *       (또는 새 어휘면 신규 그룹 신설 검토)</li>
 *   <li>본 클래스 헤더의 매트릭스 표 + 권한 어휘 커버 검증 목록 갱신</li>
 *   <li>{@code ./gradlew :backend:integrationTest --tests "kr.co.ircp.cms.security.AuthorizationMatrixExpandIT"} GREEN 확인</li>
 * </ol>
 *
 * <p>후속 SPEC {@code SPEC-CMS-SECURITY-AUTHZ-AUTODETECT-001}(가칭)에서 ArchUnit 또는
 * Spring AOT introspection 자동 검출로 본 수동 절차가 대체될 수 있다.
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001 (REQ-AM-EXP-001/002/003)
 */
// @MX:NOTE: [AUTO] AuthorizationMatrixExpandIT — 운영 SecurityFilterChain @PreAuthorize 12 권한 어휘 회귀 검출 IT (확장)
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("HTTP 권한 매트릭스 IT 확장 (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-001)")
class AuthorizationMatrixExpandIT {

    // ─── Testcontainers PostgreSQL 16 (운영 동등 Flyway 마이그레이션 적용) ──────────────
    // AUTHZ-MATRIX-001 패턴 100% 재사용 — singleton 컨테이너 패턴
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

    // JwtTokenProvider/TokenBlacklistMapper는 Mock으로 대체 — DB 토큰 저장 없이 권한 시나리오만 검증
    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    @SuppressWarnings("unused") // Step 2~4에서 활성화될 시나리오에서 사용 예정
    private static final String VALID_TOKEN = "valid.jwt.token";

    // ─── JWT Mock helper (AUTHZ-MATRIX-001 패턴 100% 재사용) ────────────────────────
    /**
     * 주어진 roles/permissions로 valid 토큰을 시뮬레이션한다.
     *
     * <p>Mock된 {@link JwtTokenProvider#validateAccessToken(String)}이 {@link JwtTokenProvider.JwtClaims}
     * 를 반환하도록 stub하여 운영 {@code JwtAuthenticationFilter}가 SecurityContext에 인증 principal을
     * 설정하게 한다. {@link TokenBlacklistMapper#exists(String)}은 항상 false 반환.
     *
     * <p>본 helper는 AUTHZ-MATRIX-001 {@link AuthorizationMatrixIT}의 동명 helper와 시그니처/동작 동일.
     * 별도 helper 클래스 분리 대신 클래스 내부 private 메소드로 둠 — AUTHZ-MATRIX-001 패턴 일관.
     *
     * @param roles       JwtPrincipal#roles — 운영에서 ROLE_&lt;role&gt; authority로 변환됨
     * @param permissions JwtPrincipal#permissions — 운영에서 그대로 authority로 사용됨
     */
    @SuppressWarnings("unused") // Step 2~4에서 활성화될 시나리오에서 사용 예정
    private void givenValidToken(Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "testuser", roles, permissions, Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    // =================================================================================
    // §0 인프라 smoke test — Step 1에서 유일하게 활성화되는 시나리오
    // =================================================================================

    /**
     * AC-AME-002-1 (Step 1 부분): IT 컨텍스트 부팅 + JwtTokenProvider Mock 주입.
     *
     * <p>본 테스트가 실행 가능하다는 것 자체가 다음을 의미한다:
     * <ul>
     *   <li>Spring 컨텍스트 로드 GREEN (운영 SecurityConfig + JwtAuthenticationFilter 적재)</li>
     *   <li>Testcontainers PostgreSQL 16 컨테이너 시작 + Flyway 마이그레이션 적용 GREEN</li>
     *   <li>{@code @MockitoBean JwtTokenProvider/TokenBlacklistMapper} 주입 GREEN</li>
     *   <li>PII 더미 키 ({@code pii.keyvault.keys.v1/hmac-key}) 주입 GREEN — PiiKeyVault 부팅 GREEN</li>
     *   <li>{@code givenValidToken(roles, permissions)} helper 동작 GREEN</li>
     * </ul>
     *
     * <p>본 smoke test 외 30 endpoint × 3 시나리오 + 권한 어휘 회귀 검증은 Step 2~4에서 활성화된다.
     */
    @Test
    @DisplayName("§0 AC-AME-002-1: 컨텍스트 부팅 + JwtTokenProvider/TokenBlacklistMapper Mock 주입 + JwtTestAuth helper 동작")
    void contextLoadsAndJwtAuthMockable() {
        // Given/When: 컨텍스트가 부팅되어 본 메소드 진입한 시점에 모든 Bean이 주입된 상태
        // Then: 핵심 Bean 주입 + helper 동작 검증
        assertNotNull(mockMvc, "MockMvc 주입 확인 (운영 SecurityFilterChain 적재 결과)");
        assertNotNull(jwtTokenProvider, "JwtTokenProvider @MockitoBean 주입 확인");
        assertNotNull(tokenBlacklistMapper, "TokenBlacklistMapper @MockitoBean 주입 확인");

        // helper 동작 GREEN 검증 — 빈 권한 set으로 stub 호출 (예외 없으면 GREEN)
        givenValidToken(Set.of(), Set.of());
    }

    // =================================================================================
    // §A REQ-AM-EXP-001 — 30 endpoint × 3 시나리오 매트릭스 (~90 AC, Step 2~3 활성화)
    //
    // 본 RUN Step 1에서는 도메인별 @Nested 그룹의 뼈대만 신설하고 모든 시나리오는
    // @Disabled placeholder로 둔다. Step 2 (Phase A) / Step 3 (Phase B)에서 점진 활성화.
    // =================================================================================

    /**
     * §A.1 ContentDomainTests — 7 endpoint × 3 시나리오 = ~21 AC.
     *
     * <p>커버 권한 어휘: CONTENT:WRITE (Popup), PAGE:WRITE (PUT), PAGE:PUBLISH (publish/schedule/retract),
     * TEMPLATE:WRITE (POST/PUT).
     *
     * <p>Step 2 (Phase A)에서 PAGE:WRITE / PAGE:PUBLISH / CONTENT:WRITE 시나리오 활성화.
     * <br>Step 3 (Phase B)에서 TEMPLATE:WRITE 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.1 ContentDomainTests (7 endpoint × 3 시나리오)")
    class ContentDomainTests {

        /**
         * TODO Step 2 (Phase A): 7 endpoint × 3 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>POST /api/v1/content/popups — CONTENT:WRITE (PopupController#create)</li>
         *   <li>PUT /api/v1/content/pages/{id} — PAGE:WRITE (PageController#update)</li>
         *   <li>POST /api/v1/content/pages/{id}/publish — PAGE:PUBLISH (PageController#publish)</li>
         *   <li>POST /api/v1/content/pages/{id}/schedule — PAGE:PUBLISH (PageController#schedule)</li>
         *   <li>POST /api/v1/content/pages/{id}/retract — PAGE:PUBLISH (PageController#retract)</li>
         *   <li>POST /api/v1/content/templates — TEMPLATE:WRITE (TemplateController#create) — Step 3</li>
         *   <li>PUT /api/v1/content/templates/{id} — TEMPLATE:WRITE (TemplateController#update) — Step 3</li>
         * </ul>
         */
        @Test
        @Disabled("Step 2 (Phase A) / Step 3 (Phase B)에서 활성화 예정 — 7 endpoint × 3 시나리오 매트릭스")
        @DisplayName("§A.1 placeholder: Step 2~3 활성화 대기")
        void contentDomain_placeholder_step2to3() {
            // 본 placeholder는 도메인 그룹 뼈대 검증 목적으로 존재.
            // 실제 7 endpoint × 3 시나리오는 Step 2 (Phase A: PAGE/CONTENT) + Step 3 (Phase B: TEMPLATE)에서 활성화.
        }
    }

    /**
     * §A.2 BlockDomainTests — 2 endpoint × 3 시나리오 = ~6 AC.
     *
     * <p>커버 권한 어휘: BLOCK:WRITE (POST/PUT).
     *
     * <p>Step 3 (Phase B)에서 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.2 BlockDomainTests (2 endpoint × 3 시나리오)")
    class BlockDomainTests {

        /**
         * TODO Step 3 (Phase B): 2 endpoint × 3 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>POST /api/v1/content/pages/{pageId}/blocks — BLOCK:WRITE (ContentBlockController#create)</li>
         *   <li>PUT /api/v1/content/pages/{pageId}/blocks/{blockId} — BLOCK:WRITE (ContentBlockController#update)</li>
         * </ul>
         *
         * <p>BLOCK:WRITE와 PAGE:WRITE가 별개 권한 어휘임을 검증 (PAGE:WRITE 보유 → BLOCK:WRITE 부재 → 403).
         */
        @Test
        @Disabled("Step 3 (Phase B)에서 활성화 예정 — BLOCK:WRITE 어휘 회귀 검출 (PAGE:WRITE와 분리 검증)")
        @DisplayName("§A.2 placeholder: Step 3 활성화 대기")
        void blockDomain_placeholder_step3() {
            // BLOCK:WRITE 어휘 분리 회귀 — Step 3에서 활성화.
        }
    }

    /**
     * §A.3 DashboardDomainTests — 3 endpoint × 3 시나리오 = ~9 AC.
     *
     * <p>커버 권한 어휘: hasRole('SUPER_ADMIN') (Widget POST), hasAnyRole('SUPER_ADMIN','DEPT_ADMIN') (Widget PUT),
     * SYSTEM:STATS (Stats GET).
     *
     * <p>Step 2 (Phase A)에서 SUPER_ADMIN/hasAnyRole 시나리오 활성화.
     * <br>Step 3 (Phase B)에서 SYSTEM:STATS 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.3 DashboardDomainTests (3 endpoint × 3 시나리오)")
    class DashboardDomainTests {

        /**
         * TODO Step 2~3: 3 endpoint × 3 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>POST /api/v1/dashboard/widgets — hasRole('SUPER_ADMIN') (DashboardWidgetController#create) — Step 2</li>
         *   <li>PUT /api/v1/dashboard/widgets/{id} — hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')
         *       (DashboardWidgetController#update) — Step 2 multi-role 부분 매칭 회귀</li>
         *   <li>GET /api/v1/system/stats/trend — SYSTEM:STATS (StatsController#trend) — Step 3</li>
         * </ul>
         *
         * <p>multi-role 회귀 검증: AC-AME-001-D3에서 DEPT_ADMIN 단독 보유 → 200 통과 검증
         * (SUPER_ADMIN 없이도 정책 통과).
         */
        @Test
        @Disabled("Step 2~3에서 활성화 예정 — Dashboard SUPER_ADMIN/hasAnyRole/SYSTEM:STATS 매트릭스")
        @DisplayName("§A.3 placeholder: Step 2~3 활성화 대기")
        void dashboardDomain_placeholder_step2to3() {
            // SUPER_ADMIN + hasAnyRole multi-role + SYSTEM:STATS 어휘 회귀 — Step 2~3 활성화.
        }
    }

    /**
     * §A.4 AuthDomainTests — 4 endpoint × 시나리오 ≈ 12 AC (isAuthenticated 어휘 403 N/A 제외).
     *
     * <p>커버 권한 어휘: hasRole('SUPER_ADMIN') (force-logout/Organization create),
     * isAuthenticated() (Qna GET/POST — 401/200만, 403 N/A).
     *
     * <p>Step 2 (Phase A)에서 SUPER_ADMIN 시나리오 활성화.
     * <br>Step 3 (Phase B)에서 isAuthenticated 401/200 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.4 AuthDomainTests (4 endpoint, isAuthenticated 403 N/A)")
    class AuthDomainTests {

        /**
         * TODO Step 2~3: 4 endpoint × 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>POST /api/v1/users/{id}/force-logout — hasRole('SUPER_ADMIN')
         *       (UserController#forceLogout) — Step 2 (AUTHZ-MATRIX-001 User register와 다른 endpoint)</li>
         *   <li>POST /api/v1/organizations — hasRole('SUPER_ADMIN')
         *       (OrganizationController#create) — Step 2 (다른 컨트롤러 보강)</li>
         *   <li>GET /api/v1/qnas — isAuthenticated() (QnaController#list) — Step 3 (401 + 200만)</li>
         *   <li>POST /api/v1/qnas — isAuthenticated() (QnaController#create) — Step 3 (401 + 200만)</li>
         * </ul>
         *
         * <p>isAuthenticated() 어휘 특이 케이스: 정책상 권한 무관이므로 403 시나리오는 N/A.
         * <br>401 시나리오 (Authorization 헤더 부재) + 200 시나리오 (유효 JWT, 권한 무관) 두 시나리오만 검증.
         */
        @Test
        @Disabled("Step 2~3에서 활성화 예정 — Auth SUPER_ADMIN + isAuthenticated 매트릭스 (isAuthenticated 403 N/A)")
        @DisplayName("§A.4 placeholder: Step 2~3 활성화 대기")
        void authDomain_placeholder_step2to3() {
            // SUPER_ADMIN + isAuthenticated 어휘 회귀 — Step 2~3 활성화.
        }
    }

    /**
     * §A.5 SystemDomainTests — 5 endpoint × 3 시나리오 = ~15 AC.
     *
     * <p>커버 권한 어휘: SYSTEM:CODE:READ (Code/CodeGroup list), SYSTEM:CODE:WRITE (Code POST/PUT, CodeGroup POST).
     *
     * <p>Step 2 (Phase A)에서 SYSTEM:CODE:READ 시나리오 활성화.
     * <br>Step 3 (Phase B)에서 SYSTEM:CODE:WRITE 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.5 SystemDomainTests (5 endpoint × 3 시나리오)")
    class SystemDomainTests {

        /**
         * TODO Step 2~3: 5 endpoint × 3 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>GET /api/v1/system/codes — SYSTEM:CODE:READ (CodeController#list) — Step 2</li>
         *   <li>GET /api/v1/system/code-groups — SYSTEM:CODE:READ (CodeGroupController#list) — Step 2</li>
         *   <li>POST /api/v1/system/codes — SYSTEM:CODE:WRITE (CodeController#create) — Step 3</li>
         *   <li>PUT /api/v1/system/codes/{id} — SYSTEM:CODE:WRITE (CodeController#update) — Step 3</li>
         *   <li>POST /api/v1/system/code-groups — SYSTEM:CODE:WRITE (CodeGroupController#create) — Step 3</li>
         * </ul>
         *
         * <p>SYSTEM:CODE:READ와 SYSTEM:CODE:WRITE 분리 회귀: READ 권한만 보유한 토큰이
         * POST/PUT (WRITE 정책 endpoint)에 대해 403 반환되어야 함 (권한 어휘 분리 회귀 검출).
         */
        @Test
        @Disabled("Step 2~3에서 활성화 예정 — System CODE:READ vs CODE:WRITE 어휘 분리 회귀 매트릭스")
        @DisplayName("§A.5 placeholder: Step 2~3 활성화 대기")
        void systemDomain_placeholder_step2to3() {
            // SYSTEM:CODE:READ vs SYSTEM:CODE:WRITE 어휘 분리 회귀 — Step 2~3 활성화.
        }
    }

    /**
     * §A.6 GovernanceDomainTests — 3 endpoint × 3 시나리오 = ~9 AC.
     *
     * <p>커버 권한 어휘: hasRole('ADMIN') (DataQuality 클래스 레벨, RecoveryDrill 클래스 레벨).
     *
     * <p>Step 2 (Phase A)에서 ADMIN 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.6 GovernanceDomainTests (3 endpoint × 3 시나리오)")
    class GovernanceDomainTests {

        /**
         * TODO Step 2 (Phase A): 3 endpoint × 3 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>GET /api/v1/governance/quality-rules — hasRole('ADMIN') 클래스 레벨
         *       (DataQualityController) — AUTHZ-MATRIX-001 RetentionPolicy와 다른 컨트롤러 보강</li>
         *   <li>POST /api/v1/governance/quality-rules — hasRole('ADMIN') 클래스 레벨</li>
         *   <li>POST /api/v1/governance/recovery-drills — hasRole('ADMIN') 클래스 레벨
         *       (RecoveryDrillController) — 다른 컨트롤러 보강</li>
         * </ul>
         *
         * <p>클래스 레벨 @PreAuthorize 운영 적재 회귀 검증: 운영 SecurityFilterChain이
         * 클래스 레벨 어노테이션도 인터셉트하는지 회귀 (메소드 레벨만 회귀해도 RED 신호 발생해야 함).
         */
        @Test
        @Disabled("Step 2 (Phase A)에서 활성화 예정 — Governance hasRole('ADMIN') 클래스 레벨 매트릭스")
        @DisplayName("§A.6 placeholder: Step 2 활성화 대기")
        void governanceDomain_placeholder_step2() {
            // hasRole('ADMIN') 클래스 레벨 어휘 회귀 — Step 2 활성화.
        }
    }

    /**
     * §A.7 BoardMenuDomainTests — 5 endpoint × 3 시나리오 = ~15 AC.
     *
     * <p>커버 권한 어휘: MENU:WRITE (Menu POST/PATCH/DELETE), hasRole('ADMIN') (Board POST/PUT — 다른 컨트롤러 보강).
     *
     * <p>Step 2 (Phase A)에서 ADMIN (Board) 시나리오 활성화.
     * <br>Step 3 (Phase B)에서 MENU:WRITE 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.7 BoardMenuDomainTests (5 endpoint × 3 시나리오)")
    class BoardMenuDomainTests {

        /**
         * TODO Step 2~3: 5 endpoint × 3 시나리오 활성화 예정.
         *
         * <ul>
         *   <li>POST /api/v1/content/menus — MENU:WRITE (MenuController#create) — Step 3</li>
         *   <li>PATCH /api/v1/content/menus/{id}/order — MENU:WRITE (MenuController#reorder) — Step 3</li>
         *   <li>DELETE /api/v1/content/menus/{id} — MENU:WRITE (MenuController#delete) — Step 3</li>
         *   <li>POST /api/v1/boards — hasRole('ADMIN') (BbsMasterController#create) — Step 2 (ADMIN 다른 컨트롤러 보강)</li>
         *   <li>PUT /api/v1/boards/{id} — hasRole('ADMIN') (BbsMasterController#update) — Step 2</li>
         * </ul>
         *
         * <p>MENU:WRITE 어휘 회귀 검증: CONTENT:WRITE 또는 PAGE:WRITE 보유 토큰이 MENU 정책 endpoint에서
         * 403 반환되어야 함 (어휘 분리 회귀).
         */
        @Test
        @Disabled("Step 2~3에서 활성화 예정 — BoardMenu MENU:WRITE + hasRole('ADMIN') 매트릭스")
        @DisplayName("§A.7 placeholder: Step 2~3 활성화 대기")
        void boardMenuDomain_placeholder_step2to3() {
            // MENU:WRITE + hasRole('ADMIN') 어휘 회귀 — Step 2~3 활성화.
        }
    }
}
