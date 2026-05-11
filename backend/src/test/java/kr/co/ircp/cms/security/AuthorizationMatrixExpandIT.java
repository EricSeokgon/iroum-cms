package kr.co.ircp.cms.security;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    // §A REQ-AM-EXP-001 — 29 endpoint × 3 시나리오 매트릭스 (Step 2~3 모두 활성화 완료)
    //
    // 도메인별 @Nested 그룹 7개에 권한 어휘 12종 모두 활성화 완료 (Step 2 + Step 3).
    // 합계 약 88 AC + smoke test 1건 = 89 AC.
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
    @DisplayName("§A.1 ContentDomainTests (Step 2 Phase A: 5 endpoint × 3 시나리오 = 15 AC, TEMPLATE 2건은 Step 3)")
    class ContentDomainTests {

        // ─── 1. POST /api/v1/content/popups — CONTENT:WRITE (다른 컨트롤러 보강) ──

        /** AC-AME-001-A1-1: Popup 생성 — 토큰 부재 → 401 AUTH_REQUIRED. */
        @Test
        @DisplayName("AC-AME-001-A1-1: POST /api/v1/content/popups — Authorization 헤더 부재 + 401")
        void popupCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/popups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A1-2: Popup 생성 — CONTENT:WRITE 부재 → 403 AUTH_FORBIDDEN. */
        @Test
        @DisplayName("AC-AME-001-A1-2: POST /api/v1/content/popups — CONTENT:WRITE 부재 + 403")
        void popupCreate_forbidden_whenContentWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // CONTENT:WRITE 미보유

            mockMvc.perform(post("/api/v1/content/popups")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-3: Popup 생성 — CONTENT:WRITE 보유 → 401/403 외(권한 통과). */
        @Test
        @DisplayName("AC-AME-001-A1-3: POST /api/v1/content/popups — CONTENT:WRITE 보유 + 401/403 아님")
        void popupCreate_passesAuthorization_whenContentWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));

            mockMvc.perform(post("/api/v1/content/popups")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. PUT /api/v1/content/pages/{id} — PAGE:WRITE ──────────────────────

        /** AC-AME-001-A1-4: Page 수정 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A1-4: PUT /api/v1/content/pages/{id} — Authorization 헤더 부재 + 401")
        void pageUpdate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(put("/api/v1/content/pages/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A1-5: Page 수정 — PAGE:WRITE 부재(CONTENT:WRITE만 보유) → 403.
         * 권한 어휘 분리 회귀 검증: CONTENT:WRITE와 PAGE:WRITE는 별개 어휘.
         */
        @Test
        @DisplayName("AC-AME-001-A1-5: PUT /api/v1/content/pages/{id} — PAGE:WRITE 부재(CONTENT:WRITE만) + 403")
        void pageUpdate_forbidden_whenPageWriteMissing() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE")); // PAGE:WRITE 미보유

            mockMvc.perform(put("/api/v1/content/pages/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-6: Page 수정 — PAGE:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A1-6: PUT /api/v1/content/pages/{id} — PAGE:WRITE 보유 + 401/403 아님")
        void pageUpdate_passesAuthorization_whenPageWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:WRITE"));

            mockMvc.perform(put("/api/v1/content/pages/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 3. POST /api/v1/content/pages/{id}/publish — PAGE:PUBLISH ───────────

        /** AC-AME-001-A1-7: Page publish — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A1-7: POST /api/v1/content/pages/{id}/publish — Authorization 헤더 부재 + 401")
        void pagePublish_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/pages/1/publish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A1-8: Page publish — PAGE:PUBLISH 부재(PAGE:WRITE만 보유) → 403.
         * 권한 어휘 분리 회귀 검증 (핵심): PAGE:WRITE와 PAGE:PUBLISH는 별개 어휘 — 발행 권한 분리.
         */
        @Test
        @DisplayName("AC-AME-001-A1-8: POST /api/v1/content/pages/{id}/publish — PAGE:PUBLISH 부재(PAGE:WRITE만) + 403 (어휘 분리 회귀)")
        void pagePublish_forbidden_whenPagePublishMissing_separationFromPageWrite() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:WRITE")); // PAGE:PUBLISH 미보유

            mockMvc.perform(post("/api/v1/content/pages/1/publish")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-9: Page publish — PAGE:PUBLISH 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A1-9: POST /api/v1/content/pages/{id}/publish — PAGE:PUBLISH 보유 + 401/403 아님")
        void pagePublish_passesAuthorization_whenPagePublishPresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:PUBLISH"));

            mockMvc.perform(post("/api/v1/content/pages/1/publish")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 4. POST /api/v1/content/pages/{id}/schedule — PAGE:PUBLISH ──────────

        /** AC-AME-001-A1-10: Page schedule — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A1-10: POST /api/v1/content/pages/{id}/schedule — Authorization 헤더 부재 + 401")
        void pageSchedule_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/pages/1/schedule")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A1-11: Page schedule — PAGE:PUBLISH 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A1-11: POST /api/v1/content/pages/{id}/schedule — PAGE:PUBLISH 부재 + 403")
        void pageSchedule_forbidden_whenPagePublishMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(post("/api/v1/content/pages/1/schedule")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-12: Page schedule — PAGE:PUBLISH 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A1-12: POST /api/v1/content/pages/{id}/schedule — PAGE:PUBLISH 보유 + 401/403 아님")
        void pageSchedule_passesAuthorization_whenPagePublishPresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:PUBLISH"));

            mockMvc.perform(post("/api/v1/content/pages/1/schedule")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 5. POST /api/v1/content/pages/{id}/retract — PAGE:PUBLISH ───────────

        /** AC-AME-001-A1-13: Page retract — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A1-13: POST /api/v1/content/pages/{id}/retract — Authorization 헤더 부재 + 401")
        void pageRetract_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/pages/1/retract")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A1-14: Page retract — PAGE:PUBLISH 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A1-14: POST /api/v1/content/pages/{id}/retract — PAGE:PUBLISH 부재 + 403")
        void pageRetract_forbidden_whenPagePublishMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(post("/api/v1/content/pages/1/retract")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-15: Page retract — PAGE:PUBLISH 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A1-15: POST /api/v1/content/pages/{id}/retract — PAGE:PUBLISH 보유 + 401/403 아님")
        void pageRetract_passesAuthorization_whenPagePublishPresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:PUBLISH"));

            mockMvc.perform(post("/api/v1/content/pages/1/retract")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.1 Step 2 Phase A 합계: 5 endpoint × 3 시나리오 = 15 AC
        // (PAGE:WRITE/PAGE:PUBLISH 어휘 분리 회귀는 AC-AME-001-A1-8에 통합)

        // ─── 6. POST /api/v1/content/templates — TEMPLATE:WRITE (Step 3) ────────

        /** AC-AME-001-A1-16: Template 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A1-16: POST /api/v1/content/templates — Authorization 헤더 부재 + 401")
        void templateCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/templates")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A1-17: Template 생성 — PAGE:WRITE만 → 403 (어휘 분리 회귀). */
        @Test
        @DisplayName("AC-AME-001-A1-17: POST /api/v1/content/templates — TEMPLATE:WRITE 부재(PAGE:WRITE만) + 403")
        void templateCreate_forbidden_whenTemplateWriteMissing() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:WRITE"));

            mockMvc.perform(post("/api/v1/content/templates")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-18: Template 생성 — TEMPLATE:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A1-18: POST /api/v1/content/templates — TEMPLATE:WRITE 보유 + 401/403 아님")
        void templateCreate_passesAuthorization_whenTemplateWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("TEMPLATE:WRITE"));

            mockMvc.perform(post("/api/v1/content/templates")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 7. PUT /api/v1/content/templates/{id} — TEMPLATE:WRITE (Step 3) ────

        /** AC-AME-001-A1-19: Template 수정 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A1-19: PUT /api/v1/content/templates/{id} — Authorization 헤더 부재 + 401")
        void templateUpdate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(put("/api/v1/content/templates/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A1-20: Template 수정 — USER 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A1-20: PUT /api/v1/content/templates/{id} — TEMPLATE:WRITE 부재 + 403")
        void templateUpdate_forbidden_whenTemplateWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(put("/api/v1/content/templates/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A1-21: Template 수정 — TEMPLATE:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A1-21: PUT /api/v1/content/templates/{id} — TEMPLATE:WRITE 보유 + 401/403 아님")
        void templateUpdate_passesAuthorization_whenTemplateWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("TEMPLATE:WRITE"));

            mockMvc.perform(put("/api/v1/content/templates/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.1 합계: 7 endpoint × 3 시나리오 = 21 AC (Phase A 15 + Phase B Template 6)
    }

    /**
     * §A.2 BlockDomainTests — 2 endpoint × 3 시나리오 = ~6 AC.
     *
     * <p>커버 권한 어휘: BLOCK:WRITE (POST/PUT).
     *
     * <p>Step 3 (Phase B)에서 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.2 BlockDomainTests (2 endpoint × 3 시나리오 = 6 AC)")
    class BlockDomainTests {

        // ─── 1. POST /api/v1/content/pages/{pageId}/blocks — BLOCK:WRITE ───────

        /** AC-AME-001-A2-1: Block 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A2-1: POST /api/v1/content/pages/{pageId}/blocks — Authorization 헤더 부재 + 401")
        void blockCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/pages/1/blocks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A2-2: Block 생성 — PAGE:WRITE만 보유(BLOCK:WRITE 부재) → 403.
         * 어휘 분리 회귀 검증: BLOCK:WRITE는 PAGE:WRITE와 별개 어휘.
         */
        @Test
        @DisplayName("AC-AME-001-A2-2: POST /api/v1/content/pages/{pageId}/blocks — BLOCK:WRITE 부재(PAGE:WRITE만) + 403 (어휘 분리)")
        void blockCreate_forbidden_whenBlockWriteMissing_separationFromPageWrite() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:WRITE")); // BLOCK:WRITE 미보유

            mockMvc.perform(post("/api/v1/content/pages/1/blocks")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A2-3: Block 생성 — BLOCK:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A2-3: POST /api/v1/content/pages/{pageId}/blocks — BLOCK:WRITE 보유 + 401/403 아님")
        void blockCreate_passesAuthorization_whenBlockWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("BLOCK:WRITE"));

            mockMvc.perform(post("/api/v1/content/pages/1/blocks")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. PUT /api/v1/content/pages/{pageId}/blocks/{blockId} — BLOCK:WRITE ──

        /** AC-AME-001-A2-4: Block 수정 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A2-4: PUT /api/v1/content/pages/{pageId}/blocks/{blockId} — Authorization 헤더 부재 + 401")
        void blockUpdate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(put("/api/v1/content/pages/1/blocks/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A2-5: Block 수정 — USER 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A2-5: PUT /api/v1/content/pages/{pageId}/blocks/{blockId} — BLOCK:WRITE 부재 + 403")
        void blockUpdate_forbidden_whenBlockWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(put("/api/v1/content/pages/1/blocks/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A2-6: Block 수정 — BLOCK:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A2-6: PUT /api/v1/content/pages/{pageId}/blocks/{blockId} — BLOCK:WRITE 보유 + 401/403 아님")
        void blockUpdate_passesAuthorization_whenBlockWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("BLOCK:WRITE"));

            mockMvc.perform(put("/api/v1/content/pages/1/blocks/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.2 합계: 2 endpoint × 3 시나리오 = 6 AC (BLOCK:WRITE vs PAGE:WRITE 어휘 분리 회귀 통합)
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
    @DisplayName("§A.3 DashboardDomainTests (Step 2 부분: 2 endpoint × 3~4 시나리오 = 7 AC, SYSTEM:STATS는 Step 3)")
    class DashboardDomainTests {

        // ─── 1. POST /api/v1/dashboard/widgets — hasRole('SUPER_ADMIN') ─────────

        /** AC-AME-001-A3-1: Widget 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A3-1: POST /api/v1/dashboard/widgets — Authorization 헤더 부재 + 401")
        void widgetCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/dashboard/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A3-2: Widget 생성 — DEPT_ADMIN 단독 → 403.
         * hasRole('SUPER_ADMIN') vs hasAnyRole('SUPER_ADMIN','DEPT_ADMIN') 어휘 분리 회귀:
         * Widget POST는 SUPER_ADMIN만 허용 (DEPT_ADMIN 거부).
         */
        @Test
        @DisplayName("AC-AME-001-A3-2: POST /api/v1/dashboard/widgets — DEPT_ADMIN 단독 + 403 (hasRole vs hasAnyRole 분리)")
        void widgetCreate_forbidden_whenOnlyDeptAdmin() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of()); // SUPER_ADMIN 부재

            mockMvc.perform(post("/api/v1/dashboard/widgets")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A3-3: Widget 생성 — SUPER_ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A3-3: POST /api/v1/dashboard/widgets — SUPER_ADMIN 보유 + 401/403 아님")
        void widgetCreate_passesAuthorization_whenSuperAdmin() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/dashboard/widgets")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. PUT /api/v1/dashboard/widgets/{id} — hasAnyRole('SUPER_ADMIN','DEPT_ADMIN') ──

        /** AC-AME-001-A3-4: Widget 수정 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A3-4: PUT /api/v1/dashboard/widgets/{id} — Authorization 헤더 부재 + 401")
        void widgetUpdate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(put("/api/v1/dashboard/widgets/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A3-5: Widget 수정 — USER 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A3-5: PUT /api/v1/dashboard/widgets/{id} — USER 역할 + 403")
        void widgetUpdate_forbidden_whenNotAdminRole() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(put("/api/v1/dashboard/widgets/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A3-6: Widget 수정 — SUPER_ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A3-6: PUT /api/v1/dashboard/widgets/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void widgetUpdate_passesAuthorization_whenSuperAdmin() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());

            mockMvc.perform(put("/api/v1/dashboard/widgets/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        /**
         * AC-AME-001-A3-7 (multi-role 핵심 회귀): Widget 수정 — DEPT_ADMIN 단독 → 401/403 외.
         * hasAnyRole 어휘 분기 회귀 검증: SUPER_ADMIN 없이 DEPT_ADMIN만으로도 통과해야 함.
         */
        @Test
        @DisplayName("AC-AME-001-A3-7: PUT /api/v1/dashboard/widgets/{id} — DEPT_ADMIN 단독 + 401/403 아님 (hasAnyRole 분기)")
        void widgetUpdate_passesAuthorization_whenOnlyDeptAdmin_multiRoleBranch() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of()); // SUPER_ADMIN 없이도 통과

            mockMvc.perform(put("/api/v1/dashboard/widgets/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.3 Step 2 합계: 2 endpoint × 3~4 시나리오 = 7 AC (Widget POST 3 + Widget PUT 4 multi-role)

        // ─── 3. GET /api/v1/system/stats/trend — SYSTEM:STATS (Step 3) ──────────

        /** AC-AME-001-A3-8: Stats trend — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A3-8: GET /api/v1/system/stats/trend — Authorization 헤더 부재 + 401")
        void statsTrend_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/system/stats/trend"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A3-9: Stats trend — SYSTEM:STATS 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A3-9: GET /api/v1/system/stats/trend — SYSTEM:STATS 부재 + 403")
        void statsTrend_forbidden_whenSystemStatsMissing() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));

            mockMvc.perform(get("/api/v1/system/stats/trend")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A3-10: Stats trend — SYSTEM:STATS 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A3-10: GET /api/v1/system/stats/trend — SYSTEM:STATS 보유 + 401/403 아님")
        void statsTrend_passesAuthorization_whenSystemStatsPresent() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("SYSTEM:STATS"));

            mockMvc.perform(get("/api/v1/system/stats/trend")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.3 합계: 3 endpoint × 3~4 시나리오 = 10 AC (Phase A 7 + Phase B Stats 3)
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
    @DisplayName("§A.4 AuthDomainTests (Step 2 부분: SUPER_ADMIN 2 endpoint × 3 시나리오 = 6 AC, isAuthenticated는 Step 3)")
    class AuthDomainTests {

        // ─── 1. POST /api/v1/users/{id}/force-logout — hasRole('SUPER_ADMIN') ──

        /** AC-AME-001-A4-1: User 강제 로그아웃 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A4-1: POST /api/v1/users/{id}/force-logout — Authorization 헤더 부재 + 401")
        void userForceLogout_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/users/1/force-logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A4-2: User 강제 로그아웃 — ADMIN 역할 → 403.
         * SUPER_ADMIN/ADMIN 역할 위계 회귀: ADMIN은 SUPER_ADMIN 정책 미충족.
         */
        @Test
        @DisplayName("AC-AME-001-A4-2: POST /api/v1/users/{id}/force-logout — ADMIN 역할(SUPER_ADMIN 부재) + 403")
        void userForceLogout_forbidden_whenNotSuperAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/users/1/force-logout")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A4-3: User 강제 로그아웃 — SUPER_ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A4-3: POST /api/v1/users/{id}/force-logout — SUPER_ADMIN + 401/403 아님")
        void userForceLogout_passesAuthorization_whenSuperAdmin() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/users/1/force-logout")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. POST /api/v1/organizations — hasRole('SUPER_ADMIN') ─────────────

        /** AC-AME-001-A4-4: Organization 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A4-4: POST /api/v1/organizations — Authorization 헤더 부재 + 401")
        void organizationCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/organizations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A4-5: Organization 생성 — DEPT_ADMIN 단독 → 403. */
        @Test
        @DisplayName("AC-AME-001-A4-5: POST /api/v1/organizations — DEPT_ADMIN 단독 + 403")
        void organizationCreate_forbidden_whenNotSuperAdmin() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/organizations")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A4-6: Organization 생성 — SUPER_ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A4-6: POST /api/v1/organizations — SUPER_ADMIN + 401/403 아님")
        void organizationCreate_passesAuthorization_whenSuperAdmin() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/organizations")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.4 Step 2 합계: 2 endpoint × 3 시나리오 = 6 AC

        // ─── 3. GET /api/v1/qnas — isAuthenticated() (Step 3, 401/200만 — 403 N/A) ──

        /** AC-AME-001-A4-7: Qna 목록 조회 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A4-7: GET /api/v1/qnas — Authorization 헤더 부재 + 401")
        void qnaList_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/qnas"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A4-8: Qna 목록 조회 — 유효 토큰(권한 무관) → 401/403 외.
         * isAuthenticated() 어휘 특성: 권한 무관, 인증만 요구.
         */
        @Test
        @DisplayName("AC-AME-001-A4-8: GET /api/v1/qnas — 유효 토큰(권한 무관) + 401/403 아님")
        void qnaList_passesAuthorization_whenAuthenticated() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // 권한 무관, 인증만 충분

            mockMvc.perform(get("/api/v1/qnas")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 4. POST /api/v1/qnas — isAuthenticated() (Step 3, 401/200만 — 403 N/A) ──

        /** AC-AME-001-A4-9: Qna 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A4-9: POST /api/v1/qnas — Authorization 헤더 부재 + 401")
        void qnaCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/qnas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A4-10: Qna 생성 — 유효 토큰(권한 무관) → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A4-10: POST /api/v1/qnas — 유효 토큰(권한 무관) + 401/403 아님")
        void qnaCreate_passesAuthorization_whenAuthenticated() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(post("/api/v1/qnas")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.4 합계: 4 endpoint × 시나리오 = 10 AC (Phase A SUPER_ADMIN 6 + Phase B isAuthenticated 4 — 403 N/A)
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
    @DisplayName("§A.5 SystemDomainTests (5 endpoint × 3 시나리오 = 15 AC, READ vs WRITE 분리 회귀 통합)")
    class SystemDomainTests {

        // ─── 1. GET /api/v1/system/codes — SYSTEM:CODE:READ ─────────────────────

        /** AC-AME-001-A5-1: Code 목록 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A5-1: GET /api/v1/system/codes — Authorization 헤더 부재 + 401")
        void codesList_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/system/codes"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A5-2: Code 목록 — SYSTEM:CODE:READ 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A5-2: GET /api/v1/system/codes — SYSTEM:CODE:READ 부재 + 403")
        void codesList_forbidden_whenCodeReadMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(get("/api/v1/system/codes")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A5-3: Code 목록 — SYSTEM:CODE:READ 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A5-3: GET /api/v1/system/codes — SYSTEM:CODE:READ 보유 + 401/403 아님")
        void codesList_passesAuthorization_whenCodeReadPresent() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("SYSTEM:CODE:READ"));

            mockMvc.perform(get("/api/v1/system/codes")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. GET /api/v1/system/code-groups — SYSTEM:CODE:READ ───────────────

        /** AC-AME-001-A5-4: CodeGroup 목록 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A5-4: GET /api/v1/system/code-groups — Authorization 헤더 부재 + 401")
        void codeGroupsList_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/system/code-groups"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A5-5: CodeGroup 목록 — SYSTEM:CODE:READ 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A5-5: GET /api/v1/system/code-groups — SYSTEM:CODE:READ 부재 + 403")
        void codeGroupsList_forbidden_whenCodeReadMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("CONTENT:WRITE"));

            mockMvc.perform(get("/api/v1/system/code-groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A5-6: CodeGroup 목록 — SYSTEM:CODE:READ 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A5-6: GET /api/v1/system/code-groups — SYSTEM:CODE:READ 보유 + 401/403 아님")
        void codeGroupsList_passesAuthorization_whenCodeReadPresent() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("SYSTEM:CODE:READ"));

            mockMvc.perform(get("/api/v1/system/code-groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 3. POST /api/v1/system/codes — SYSTEM:CODE:WRITE ───────────────────

        /** AC-AME-001-A5-7: Code 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A5-7: POST /api/v1/system/codes — Authorization 헤더 부재 + 401")
        void codeCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/system/codes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A5-8: Code 생성 — SYSTEM:CODE:READ만 보유(WRITE 부재) → 403.
         * 어휘 분리 회귀: SYSTEM:CODE:READ와 SYSTEM:CODE:WRITE는 별개 어휘.
         */
        @Test
        @DisplayName("AC-AME-001-A5-8: POST /api/v1/system/codes — SYSTEM:CODE:WRITE 부재(READ만) + 403 (어휘 분리)")
        void codeCreate_forbidden_whenCodeWriteMissing_separationFromCodeRead() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("SYSTEM:CODE:READ")); // WRITE 미보유

            mockMvc.perform(post("/api/v1/system/codes")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A5-9: Code 생성 — SYSTEM:CODE:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A5-9: POST /api/v1/system/codes — SYSTEM:CODE:WRITE 보유 + 401/403 아님")
        void codeCreate_passesAuthorization_whenCodeWritePresent() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:WRITE"));

            mockMvc.perform(post("/api/v1/system/codes")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 4. PUT /api/v1/system/codes/{id} — SYSTEM:CODE:WRITE ───────────────

        /** AC-AME-001-A5-10: Code 수정 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A5-10: PUT /api/v1/system/codes/{id} — Authorization 헤더 부재 + 401")
        void codeUpdate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(put("/api/v1/system/codes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A5-11: Code 수정 — SYSTEM:CODE:WRITE 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A5-11: PUT /api/v1/system/codes/{id} — SYSTEM:CODE:WRITE 부재 + 403")
        void codeUpdate_forbidden_whenCodeWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("SYSTEM:CODE:READ"));

            mockMvc.perform(put("/api/v1/system/codes/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A5-12: Code 수정 — SYSTEM:CODE:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A5-12: PUT /api/v1/system/codes/{id} — SYSTEM:CODE:WRITE 보유 + 401/403 아님")
        void codeUpdate_passesAuthorization_whenCodeWritePresent() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:WRITE"));

            mockMvc.perform(put("/api/v1/system/codes/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 5. POST /api/v1/system/code-groups — SYSTEM:CODE:WRITE ─────────────

        /** AC-AME-001-A5-13: CodeGroup 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A5-13: POST /api/v1/system/code-groups — Authorization 헤더 부재 + 401")
        void codeGroupCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/system/code-groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A5-14: CodeGroup 생성 — SYSTEM:CODE:WRITE 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A5-14: POST /api/v1/system/code-groups — SYSTEM:CODE:WRITE 부재 + 403")
        void codeGroupCreate_forbidden_whenCodeWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of("SYSTEM:CODE:READ"));

            mockMvc.perform(post("/api/v1/system/code-groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A5-15: CodeGroup 생성 — SYSTEM:CODE:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A5-15: POST /api/v1/system/code-groups — SYSTEM:CODE:WRITE 보유 + 401/403 아님")
        void codeGroupCreate_passesAuthorization_whenCodeWritePresent() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of("SYSTEM:CODE:WRITE"));

            mockMvc.perform(post("/api/v1/system/code-groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.5 합계: 5 endpoint × 3 시나리오 = 15 AC
        // SYSTEM:CODE:READ vs SYSTEM:CODE:WRITE 어휘 분리 회귀 검증 통합 (AC-AME-001-A5-8)
    }

    /**
     * §A.6 GovernanceDomainTests — 3 endpoint × 3 시나리오 = ~9 AC.
     *
     * <p>커버 권한 어휘: hasRole('ADMIN') (DataQuality 클래스 레벨, RecoveryDrill 클래스 레벨).
     *
     * <p>Step 2 (Phase A)에서 ADMIN 시나리오 활성화.
     */
    @Nested
    @DisplayName("§A.6 GovernanceDomainTests (3 endpoint × 3 시나리오 = 9 AC, ADMIN 클래스 레벨)")
    class GovernanceDomainTests {

        // ─── 1. GET /api/v1/governance/quality-rules — ADMIN 클래스 레벨 ─────────

        /** AC-AME-001-A6-1: 품질 규칙 조회 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A6-1: GET /api/v1/governance/quality-rules — Authorization 헤더 부재 + 401")
        void qualityRules_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(get("/api/v1/governance/quality-rules"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A6-2: 품질 규칙 조회 — USER 역할 → 403 (클래스 레벨 ADMIN 미충족). */
        @Test
        @DisplayName("AC-AME-001-A6-2: GET /api/v1/governance/quality-rules — USER 역할 + 403")
        void qualityRules_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(get("/api/v1/governance/quality-rules")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A6-3: 품질 규칙 조회 — ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A6-3: GET /api/v1/governance/quality-rules — ADMIN + 401/403 아님")
        void qualityRules_passesAuthorization_whenAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(get("/api/v1/governance/quality-rules")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. POST /api/v1/governance/quality-rules — ADMIN 클래스 레벨 ────────

        /** AC-AME-001-A6-4: 품질 규칙 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A6-4: POST /api/v1/governance/quality-rules — Authorization 헤더 부재 + 401")
        void qualityRulesCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/governance/quality-rules")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A6-5: 품질 규칙 생성 — EDITOR 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A6-5: POST /api/v1/governance/quality-rules — EDITOR 역할 + 403")
        void qualityRulesCreate_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of());

            mockMvc.perform(post("/api/v1/governance/quality-rules")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A6-6: 품질 규칙 생성 — ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A6-6: POST /api/v1/governance/quality-rules — ADMIN + 401/403 아님")
        void qualityRulesCreate_passesAuthorization_whenAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/governance/quality-rules")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 3. POST /api/v1/governance/recovery-drills — ADMIN 클래스 레벨 ──────

        /** AC-AME-001-A6-7: 복구 훈련 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A6-7: POST /api/v1/governance/recovery-drills — Authorization 헤더 부재 + 401")
        void recoveryDrillsCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/governance/recovery-drills")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A6-8: 복구 훈련 생성 — USER 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A6-8: POST /api/v1/governance/recovery-drills — USER 역할 + 403")
        void recoveryDrillsCreate_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(post("/api/v1/governance/recovery-drills")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A6-9: 복구 훈련 생성 — ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A6-9: POST /api/v1/governance/recovery-drills — ADMIN + 401/403 아님")
        void recoveryDrillsCreate_passesAuthorization_whenAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/governance/recovery-drills")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.6 합계: 3 endpoint × 3 시나리오 = 9 AC (ADMIN 클래스 레벨 어휘 회귀)
        // 클래스 레벨 @PreAuthorize 운영 적재 회귀 검증: SecurityFilterChain이 클래스 레벨도 인터셉트.
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
    @DisplayName("§A.7 BoardMenuDomainTests (Step 2 부분: Board 2 endpoint × 3 시나리오 = 6 AC, MENU:WRITE는 Step 3)")
    class BoardMenuDomainTests {

        // ─── 1. POST /api/v1/boards — hasRole('ADMIN') ──────────────────────────

        /** AC-AME-001-A7-1: Board 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A7-1: POST /api/v1/boards — Authorization 헤더 부재 + 401")
        void boardCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/boards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A7-2: Board 생성 — USER 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A7-2: POST /api/v1/boards — USER 역할 + 403")
        void boardCreate_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(post("/api/v1/boards")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A7-3: Board 생성 — ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A7-3: POST /api/v1/boards — ADMIN + 401/403 아님")
        void boardCreate_passesAuthorization_whenAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/boards")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 2. PUT /api/v1/boards/{id} — hasRole('ADMIN') ──────────────────────

        /** AC-AME-001-A7-4: Board 수정 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A7-4: PUT /api/v1/boards/{id} — Authorization 헤더 부재 + 401")
        void boardUpdate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(put("/api/v1/boards/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A7-5: Board 수정 — USER 역할 → 403. */
        @Test
        @DisplayName("AC-AME-001-A7-5: PUT /api/v1/boards/{id} — USER 역할 + 403")
        void boardUpdate_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(put("/api/v1/boards/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A7-6: Board 수정 — ADMIN 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A7-6: PUT /api/v1/boards/{id} — ADMIN + 401/403 아님")
        void boardUpdate_passesAuthorization_whenAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(put("/api/v1/boards/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.7 Step 2 합계: Board 2 endpoint × 3 시나리오 = 6 AC

        // ─── 3. POST /api/v1/content/menus — MENU:WRITE (Step 3) ────────────────

        /** AC-AME-001-A7-7: Menu 생성 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A7-7: POST /api/v1/content/menus — Authorization 헤더 부재 + 401")
        void menuCreate_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/menus")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AME-001-A7-8: Menu 생성 — CONTENT:WRITE만 보유(MENU:WRITE 부재) → 403.
         * 어휘 분리 회귀: MENU:WRITE는 CONTENT:WRITE/PAGE:WRITE와 별개 어휘.
         */
        @Test
        @DisplayName("AC-AME-001-A7-8: POST /api/v1/content/menus — MENU:WRITE 부재(CONTENT:WRITE만) + 403 (어휘 분리)")
        void menuCreate_forbidden_whenMenuWriteMissing_separationFromContentWrite() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));

            mockMvc.perform(post("/api/v1/content/menus")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A7-9: Menu 생성 — MENU:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A7-9: POST /api/v1/content/menus — MENU:WRITE 보유 + 401/403 아님")
        void menuCreate_passesAuthorization_whenMenuWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("MENU:WRITE"));

            mockMvc.perform(post("/api/v1/content/menus")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 4. PATCH /api/v1/content/menus/{id}/order — MENU:WRITE ─────────────

        /** AC-AME-001-A7-10: Menu 순서 변경 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A7-10: PATCH /api/v1/content/menus/{id}/order — Authorization 헤더 부재 + 401")
        void menuReorder_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(patch("/api/v1/content/menus/1/order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A7-11: Menu 순서 변경 — MENU:WRITE 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A7-11: PATCH /api/v1/content/menus/{id}/order — MENU:WRITE 부재 + 403")
        void menuReorder_forbidden_whenMenuWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(patch("/api/v1/content/menus/1/order")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A7-12: Menu 순서 변경 — MENU:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A7-12: PATCH /api/v1/content/menus/{id}/order — MENU:WRITE 보유 + 401/403 아님")
        void menuReorder_passesAuthorization_whenMenuWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("MENU:WRITE"));

            mockMvc.perform(patch("/api/v1/content/menus/1/order")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── 5. DELETE /api/v1/content/menus/{id} — MENU:WRITE ──────────────────

        /** AC-AME-001-A7-13: Menu 삭제 — 토큰 부재 → 401. */
        @Test
        @DisplayName("AC-AME-001-A7-13: DELETE /api/v1/content/menus/{id} — Authorization 헤더 부재 + 401")
        void menuDelete_unauthorized_whenNoToken() throws Exception {
            mockMvc.perform(delete("/api/v1/content/menus/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /** AC-AME-001-A7-14: Menu 삭제 — MENU:WRITE 부재 → 403. */
        @Test
        @DisplayName("AC-AME-001-A7-14: DELETE /api/v1/content/menus/{id} — MENU:WRITE 부재 + 403")
        void menuDelete_forbidden_whenMenuWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(delete("/api/v1/content/menus/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /** AC-AME-001-A7-15: Menu 삭제 — MENU:WRITE 보유 → 401/403 외. */
        @Test
        @DisplayName("AC-AME-001-A7-15: DELETE /api/v1/content/menus/{id} — MENU:WRITE 보유 + 401/403 아님")
        void menuDelete_passesAuthorization_whenMenuWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("MENU:WRITE"));

            mockMvc.perform(delete("/api/v1/content/menus/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // §A.7 합계: 5 endpoint × 3 시나리오 = 15 AC (Phase A Board 6 + Phase B Menu 9)
        // MENU:WRITE vs CONTENT:WRITE/PAGE:WRITE 어휘 분리 회귀 검증 통합 (AC-AME-001-A7-8)
    }
}
