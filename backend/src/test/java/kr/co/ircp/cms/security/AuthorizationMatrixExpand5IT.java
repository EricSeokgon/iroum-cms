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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005 — 신규 엔드포인트 인가 IT 커버리지 복원.
 *
 * <p>장기간 백엔드 테스트 컴파일 실패로 {@code AuthorizationCoverageArchTest} 보안 회귀 가드가
 * 실행되지 못하는 동안, 운영 메서드 레벨 {@code @PreAuthorize}가 IT 인가 시나리오(401/403) 없이
 * 추가되었다. 본 IT는 ArchUnit이 추출하는 IT @DisplayName endpoint set 과 운영 엔드포인트의
 * 정합성을 복원하기 위해, baseline 110 endpoint set 에 누락된 운영 메서드 레벨 엔드포인트 28건의
 * 인가 시나리오를 추가한다.
 *
 * <p><b>스코프 정정(중요)</b>: 본 SPEC 본문은 "11개 신규 엔드포인트"로 기술되었으나, 이는
 * 메서드 레벨 {@code @PreAuthorize} 카운트 증가분(113→124 = +11)에서 도출된 값이다. 실제
 * ArchUnit {@code extractItEndpoints()} 추출 set(110) 대비 운영 메서드 레벨 정규화 endpoint
 * set(124)의 차집합은 <b>28건</b>이다. 카운트 증가분과 endpoint set 차집합이 일치하지 않는 이유는
 * 기존 113 카운트 시점에도 일부 운영 엔드포인트가 IT 미커버 상태였기 때문이다. baseline 을
 * 기계적으로 상향하는 것은 회귀 가드를 무력화하므로, 본 IT 는 미커버 28건 전체에 대해 실제
 * 401/403 인가 시나리오를 추가하여 보안 커버리지 갭을 완전히 해소한다.
 *
 * <p>패턴: AUTHZ-MATRIX-001 + EXPAND-001/002/003/004 인프라 100% 재사용.
 * <ul>
 *   <li>role/authority 게이트 엔드포인트: 401(인증 부재) + 403(권한 부족) + pass(정상 권한) 3 시나리오</li>
 *   <li>{@code isAuthenticated()} 엔드포인트: 401(인증 부재) + pass(인증 보유) 2 시나리오 (권한 분리 N/A)</li>
 * </ul>
 *
 * <p>본 IT 추가 후 {@code AuthorizationCoverageArchTest} baseline:
 * endpoint set 110 → 138, 카운트 113 → 124. 격리된 3개 메서드 재활성화.
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005 (REQ-AIE5-001/002/003/004)
 */
// @MX:NOTE: [AUTO] AuthorizationMatrixExpand5IT — IT 미커버 28 endpoint 인가 매트릭스 (확장 5차)
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.junit.jupiter.api.Tag("integration")
@DisplayName("HTTP 권한 매트릭스 IT 확장 5차 (SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005)")
class AuthorizationMatrixExpand5IT {

    // ─── Testcontainers PostgreSQL 16 (AUTHZ-MATRIX/EXPAND-001~004 패턴 일관) ─────
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

    private static final String VALID_TOKEN = "valid.jwt.token";

    // ─── JWT Mock helper (AUTHZ-IT-EXPAND-001~004 패턴 100% 재사용) ─────────────
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
    // §0 인프라 smoke test
    // =================================================================================

    @Test
    @DisplayName("§0 AC-AME5-000-1: 컨텍스트 부팅 + JwtTokenProvider/TokenBlacklistMapper Mock 주입")
    void contextLoadsAndJwtAuthMockable() {
        assertNotNull(mockMvc, "MockMvc 주입 확인 (운영 SecurityFilterChain 적재 결과)");
        assertNotNull(jwtTokenProvider, "JwtTokenProvider @MockitoBean 주입 확인");
        assertNotNull(tokenBlacklistMapper, "TokenBlacklistMapper @MockitoBean 주입 확인");
        givenValidToken(Set.of(), Set.of());
    }

    // =================================================================================
    // §A BoardDomain — PostController(isAuthenticated 3) + PostTranslationController(role 4)
    // =================================================================================

    /** §A.1 PostController — POST/PUT/DELETE (isAuthenticated, 2 시나리오). */
    @Nested
    @DisplayName("§A.1 PostDomainTests (board/posts isAuthenticated 3 endpoint)")
    class PostDomainTests {

        private static final String POST_BODY =
                "{\"bbsId\":1,\"title\":\"테스트 게시글\",\"contentHtml\":\"<p>내용</p>\"}";

        @Test
        @DisplayName("AC-AME5-A1-1: POST /api/v1/board/posts — Authorization 부재 + 401")
        void postCreate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/board/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(POST_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-A1-2: POST /api/v1/board/posts — 인증 보유 + 401/403 아님")
        void postCreate_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(post("/api/v1/board/posts")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(POST_BODY));
        }

        @Test
        @DisplayName("AC-AME5-A1-3: PUT /api/v1/board/posts/{id} — Authorization 부재 + 401")
        void postUpdate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/board/posts/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(POST_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-A1-4: PUT /api/v1/board/posts/{id} — 인증 보유 + 401/403 아님")
        void postUpdate_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(put("/api/v1/board/posts/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(POST_BODY));
        }

        @Test
        @DisplayName("AC-AME5-A1-5: DELETE /api/v1/board/posts/{id} — Authorization 부재 + 401")
        void postDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/board/posts/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-A1-6: DELETE /api/v1/board/posts/{id} — 인증 보유 + 401/403 아님")
        void postDelete_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(delete("/api/v1/board/posts/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §A.2 PostTranslationController — GET 목록/단건, PUT(role 4종), DELETE(SUPER_ADMIN). */
    @Nested
    @DisplayName("§A.2 PostTranslationDomainTests (board/posts/{id}/translations role-gated 4 endpoint)")
    class PostTranslationDomainTests {

        private static final String TRANS_BODY =
                "{\"language\":\"en\",\"title\":\"Title\",\"contentHtml\":\"<p>body</p>\"}";

        // ── GET /api/v1/board/posts/{id}/translations — hasAnyRole(CONTENT_ADMIN,DEPT_ADMIN,ADMIN,SUPER_ADMIN) ──
        // URL은 SecurityConfig에서 GET /api/v1/board/posts/** permitAll — 익명도 메서드 진입 후 @PreAuthorize 거부(403).
        @Test
        @DisplayName("AC-AME5-A2-1: GET /api/v1/board/posts/{id}/translations — 익명(권한 부재) + 403 (메서드 레벨 거부)")
        void transList_unauthenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/board/posts/1/translations"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-A2-2: GET /api/v1/board/posts/{id}/translations — USER 역할 + 403")
        void transList_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/board/posts/1/translations")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-A2-3: GET /api/v1/board/posts/{id}/translations — CONTENT_ADMIN 보유 + 401/403 아님")
        void transList_hasRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("CONTENT_ADMIN"), Set.of());
            assertAuthzPassed(get("/api/v1/board/posts/1/translations")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /api/v1/board/posts/{id}/translations/{language} (permitAll GET → 익명 메서드 거부 403) ──
        @Test
        @DisplayName("AC-AME5-A2-4: GET /api/v1/board/posts/{id}/translations/{id} — 익명(권한 부재) + 403 (메서드 레벨 거부)")
        void transGet_unauthenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/board/posts/1/translations/en"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-A2-5: GET /api/v1/board/posts/{id}/translations/{id} — USER 역할 + 403")
        void transGet_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(get("/api/v1/board/posts/1/translations/en")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-A2-6: GET /api/v1/board/posts/{id}/translations/{id} — ADMIN 보유 + 401/403 아님")
        void transGet_hasRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            assertAuthzPassed(get("/api/v1/board/posts/1/translations/en")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── PUT /api/v1/board/posts/{id}/translations ──
        @Test
        @DisplayName("AC-AME5-A2-7: PUT /api/v1/board/posts/{id}/translations — Authorization 부재 + 401")
        void transUpsert_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/v1/board/posts/1/translations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TRANS_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-A2-8: PUT /api/v1/board/posts/{id}/translations — USER 역할 + 403")
        void transUpsert_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(put("/api/v1/board/posts/1/translations")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(TRANS_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-A2-9: PUT /api/v1/board/posts/{id}/translations — DEPT_ADMIN 보유 + 401/403 아님")
        void transUpsert_hasRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            assertAuthzPassed(put("/api/v1/board/posts/1/translations")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TRANS_BODY));
        }

        // ── DELETE /api/v1/board/posts/{id}/translations/{language} — hasRole(SUPER_ADMIN) ──
        @Test
        @DisplayName("AC-AME5-A2-10: DELETE /api/v1/board/posts/{id}/translations/{id} — Authorization 부재 + 401")
        void transDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/board/posts/1/translations/en"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-A2-11: DELETE /api/v1/board/posts/{id}/translations/{id} — ADMIN 역할 + 403 (SUPER_ADMIN 전용)")
        void transDelete_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());
            mockMvc.perform(delete("/api/v1/board/posts/1/translations/en")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-A2-12: DELETE /api/v1/board/posts/{id}/translations/{id} — SUPER_ADMIN 보유 + 401/403 아님")
        void transDelete_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(delete("/api/v1/board/posts/1/translations/en")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    // =================================================================================
    // §B ContentDomain — Seo, Banner, I18n, Page, Popup, Template
    // =================================================================================

    /** §B.1 SeoRedirectController — POST/DELETE (SYSTEM:ADMIN). */
    @Nested
    @DisplayName("§B.1 SeoRedirectDomainTests (content/seo/redirects authority-gated 2 endpoint)")
    class SeoRedirectDomainTests {

        private static final String SEO_BODY =
                "{\"fromPath\":\"/old\",\"toPath\":\"/new\",\"statusCode\":301}";

        @Test
        @DisplayName("AC-AME5-B1-1: POST /api/v1/content/seo/redirects — Authorization 부재 + 401")
        void seoCreate_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/content/seo/redirects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SEO_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-B1-2: POST /api/v1/content/seo/redirects — SYSTEM:READ만 보유 + 403 (SYSTEM:ADMIN 필요)")
        void seoCreate_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(post("/api/v1/content/seo/redirects")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SEO_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B1-3: POST /api/v1/content/seo/redirects — SYSTEM:ADMIN 보유 + 401/403 아님")
        void seoCreate_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:ADMIN"));
            assertAuthzPassed(post("/api/v1/content/seo/redirects")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SEO_BODY));
        }

        @Test
        @DisplayName("AC-AME5-B1-4: DELETE /api/v1/content/seo/redirects/{id} — Authorization 부재 + 401")
        void seoDelete_unauthenticated_returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/content/seo/redirects/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-B1-5: DELETE /api/v1/content/seo/redirects/{id} — SYSTEM:READ만 보유 + 403 (SYSTEM:ADMIN 필요)")
        void seoDelete_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(delete("/api/v1/content/seo/redirects/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B1-6: DELETE /api/v1/content/seo/redirects/{id} — SYSTEM:ADMIN 보유 + 401/403 아님")
        void seoDelete_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:ADMIN"));
            assertAuthzPassed(delete("/api/v1/content/seo/redirects/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §B.2 Banner/I18n/Page/Popup/Template GET — 읽기 권한 게이트(CONTENT:READ/PAGE:READ/TEMPLATE:READ). */
    @Nested
    @DisplayName("§B.2 ContentReadDomainTests (banner/i18n/page/popup/template 읽기 5 endpoint)")
    class ContentReadDomainTests {

        // ── GET /api/v1/content/banners/groups — CONTENT:READ ──
        // URL은 GET /api/v1/content/banners/** permitAll — 익명도 메서드 진입 후 @PreAuthorize 거부(403).
        @Test
        @DisplayName("AC-AME5-B2-1: GET /api/v1/content/banners/groups — 익명(권한 부재) + 403 (메서드 레벨 거부)")
        void bannerGroups_unauthenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/content/banners/groups"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-2: GET /api/v1/content/banners/groups — 권한 부재 + 403")
        void bannerGroups_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/content/banners/groups")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-3: GET /api/v1/content/banners/groups — CONTENT:READ 보유 + 401/403 아님")
        void bannerGroups_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:READ"));
            assertAuthzPassed(get("/api/v1/content/banners/groups")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /api/v1/content/i18n/list — CONTENT:READ ──
        @Test
        @DisplayName("AC-AME5-B2-4: GET /api/v1/content/i18n/list — Authorization 부재 + 401")
        void i18nList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/i18n/list"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-B2-5: GET /api/v1/content/i18n/list — 권한 부재 + 403")
        void i18nList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            // namespace는 required @RequestParam — 누락 시 400이 인가 검증보다 먼저 발생하므로 정상 값 부여.
            mockMvc.perform(get("/api/v1/content/i18n/list")
                            .param("namespace", "common")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-6: GET /api/v1/content/i18n/list — CONTENT:READ 보유 + 401/403 아님")
        void i18nList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:READ"));
            assertAuthzPassed(get("/api/v1/content/i18n/list")
                    .param("namespace", "common")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /api/v1/content/pages — PAGE:READ ──
        @Test
        @DisplayName("AC-AME5-B2-7: GET /api/v1/content/pages — Authorization 부재 + 401")
        void pageList_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/pages"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-B2-8: GET /api/v1/content/pages — 권한 부재 + 403")
        void pageList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:READ"));
            mockMvc.perform(get("/api/v1/content/pages")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-9: GET /api/v1/content/pages — PAGE:READ 보유 + 401/403 아님")
        void pageList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("PAGE:READ"));
            assertAuthzPassed(get("/api/v1/content/pages")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /api/v1/content/popups — CONTENT:READ (siteId required param, GET permitAll → 익명 메서드 거부 403) ──
        @Test
        @DisplayName("AC-AME5-B2-10: GET /api/v1/content/popups — 익명(권한 부재) + 403 (메서드 레벨 거부)")
        void popupList_unauthenticated_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/content/popups").param("siteId", "1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-11: GET /api/v1/content/popups — 권한 부재 + 403")
        void popupList_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/content/popups")
                            .param("siteId", "1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-12: GET /api/v1/content/popups — CONTENT:READ 보유 + 401/403 아님")
        void popupList_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:READ"));
            assertAuthzPassed(get("/api/v1/content/popups")
                    .param("siteId", "1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        // ── GET /api/v1/content/templates/{id} — TEMPLATE:READ ──
        @Test
        @DisplayName("AC-AME5-B2-13: GET /api/v1/content/templates/{id} — Authorization 부재 + 401")
        void templateGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/content/templates/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-B2-14: GET /api/v1/content/templates/{id} — 권한 부재 + 403")
        void templateGet_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:READ"));
            mockMvc.perform(get("/api/v1/content/templates/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B2-15: GET /api/v1/content/templates/{id} — TEMPLATE:READ 보유 + 401/403 아님")
        void templateGet_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("TEMPLATE:READ"));
            assertAuthzPassed(get("/api/v1/content/templates/1")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §B.3 PopupController PATCH /{id}/active — CONTENT:WRITE. */
    @Nested
    @DisplayName("§B.3 PopupActiveDomainTests (content/popups/{id}/active 1 endpoint)")
    class PopupActiveDomainTests {

        @Test
        @DisplayName("AC-AME5-B3-1: PATCH /api/v1/content/popups/{id}/active — Authorization 부재 + 401")
        void popupActive_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/content/popups/1/active"))
                    .andExpect(status().isUnauthorized());
        }

        // setActive는 @RequestBody Map<String,Boolean> — body 누락 시 400이 인가 검증보다 먼저 발생하므로 정상 body 부여.
        @Test
        @DisplayName("AC-AME5-B3-2: PATCH /api/v1/content/popups/{id}/active — CONTENT:READ만 보유 + 403 (CONTENT:WRITE 필요)")
        void popupActive_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:READ"));
            mockMvc.perform(patch("/api/v1/content/popups/1/active")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"isActive\":true}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-B3-3: PATCH /api/v1/content/popups/{id}/active — CONTENT:WRITE 보유 + 401/403 아님")
        void popupActive_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("CONTENT:WRITE"));
            assertAuthzPassed(patch("/api/v1/content/popups/1/active")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"isActive\":true}"));
        }
    }

    // =================================================================================
    // §C SystemDomain — Dashboard(SYSTEM:DASHBOARD), Stats(SYSTEM:STATS), Setting(SYSTEM:SETTING:READ)
    // =================================================================================

    /** §C.1 DashboardController — trends/top-pages (SYSTEM:DASHBOARD). */
    @Nested
    @DisplayName("§C.1 DashboardStatDomainTests (system/dashboard authority-gated 2 endpoint)")
    class DashboardStatDomainTests {

        @Test
        @DisplayName("AC-AME5-C1-1: GET /api/v1/system/dashboard/trends — Authorization 부재 + 401")
        void dashTrends_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/dashboard/trends"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-C1-2: GET /api/v1/system/dashboard/trends — 권한 부재 + 403")
        void dashTrends_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/system/dashboard/trends")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-C1-3: GET /api/v1/system/dashboard/trends — SYSTEM:DASHBOARD 보유 + 401/403 아님")
        void dashTrends_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:DASHBOARD"));
            assertAuthzPassed(get("/api/v1/system/dashboard/trends")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        @Test
        @DisplayName("AC-AME5-C1-4: GET /api/v1/system/dashboard/top-pages — Authorization 부재 + 401")
        void dashTopPages_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/dashboard/top-pages"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-C1-5: GET /api/v1/system/dashboard/top-pages — 권한 부재 + 403")
        void dashTopPages_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/system/dashboard/top-pages")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-C1-6: GET /api/v1/system/dashboard/top-pages — SYSTEM:DASHBOARD 보유 + 401/403 아님")
        void dashTopPages_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:DASHBOARD"));
            assertAuthzPassed(get("/api/v1/system/dashboard/top-pages")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §C.2 StatsController — visitors/menu-pages (SYSTEM:STATS). */
    @Nested
    @DisplayName("§C.2 StatsDomainTests (system/stats authority-gated 2 endpoint)")
    class StatsDomainTests {

        @Test
        @DisplayName("AC-AME5-C2-1: GET /api/v1/system/stats/visitors — Authorization 부재 + 401")
        void statsVisitors_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/stats/visitors"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-C2-2: GET /api/v1/system/stats/visitors — 권한 부재 + 403")
        void statsVisitors_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/system/stats/visitors")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-C2-3: GET /api/v1/system/stats/visitors — SYSTEM:STATS 보유 + 401/403 아님")
        void statsVisitors_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:STATS"));
            assertAuthzPassed(get("/api/v1/system/stats/visitors")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        @Test
        @DisplayName("AC-AME5-C2-4: GET /api/v1/system/stats/menu-pages — Authorization 부재 + 401")
        void statsMenuPages_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/stats/menu-pages"))
                    .andExpect(status().isUnauthorized());
        }

        // menu-pages는 from/to required @RequestParam(LocalDate) — 누락 시 400이 인가 검증보다 먼저 발생하므로 정상 값 부여.
        @Test
        @DisplayName("AC-AME5-C2-5: GET /api/v1/system/stats/menu-pages — 권한 부재 + 403")
        void statsMenuPages_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/system/stats/menu-pages")
                            .param("from", "2026-01-01")
                            .param("to", "2026-01-31")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-C2-6: GET /api/v1/system/stats/menu-pages — SYSTEM:STATS 보유 + 401/403 아님")
        void statsMenuPages_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:STATS"));
            assertAuthzPassed(get("/api/v1/system/stats/menu-pages")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    /** §C.3 SystemSettingController — GET /{key} (SYSTEM:SETTING:READ). */
    @Nested
    @DisplayName("§C.3 SystemSettingDetailDomainTests (system/settings/{id} 1 endpoint)")
    class SystemSettingDetailDomainTests {

        @Test
        @DisplayName("AC-AME5-C3-1: GET /api/v1/system/settings/{id} — Authorization 부재 + 401")
        void settingGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/system/settings/site.title"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-C3-2: GET /api/v1/system/settings/{id} — 권한 부재 + 403")
        void settingGet_missingAuthority_returns403() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:READ"));
            mockMvc.perform(get("/api/v1/system/settings/site.title")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-C3-3: GET /api/v1/system/settings/{id} — SYSTEM:SETTING:READ 보유 + 401/403 아님")
        void settingGet_hasAuthority_passesAuthz() throws Exception {
            givenValidToken(Set.of(), Set.of("SYSTEM:SETTING:READ"));
            assertAuthzPassed(get("/api/v1/system/settings/site.title")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }
    }

    // =================================================================================
    // §D AuthUserDomain — UserController reset-password(SUPER_ADMIN), bulk-status(SUPER_ADMIN/DEPT_ADMIN)
    // =================================================================================

    @Nested
    @DisplayName("§D.1 UserAdminDomainTests (users role-gated 2 endpoint)")
    class UserAdminDomainTests {

        // reset-password는 @Valid @RequestBody AdminPasswordResetRequest(newPassword) — body 누락 시 400이
        // 인가 검증보다 먼저 발생하므로 정상 body 부여.
        private static final String RESET_BODY = "{\"newPassword\":\"NewPass123!\"}";

        @Test
        @DisplayName("AC-AME5-D1-1: POST /api/v1/users/{id}/reset-password — Authorization 부재 + 401")
        void resetPassword_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/users/1/reset-password"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-D1-2: POST /api/v1/users/{id}/reset-password — DEPT_ADMIN 역할 + 403 (SUPER_ADMIN 전용)")
        void resetPassword_missingSuperAdmin_returns403() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            mockMvc.perform(post("/api/v1/users/1/reset-password")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(RESET_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-D1-3: POST /api/v1/users/{id}/reset-password — SUPER_ADMIN 보유 + 401/403 아님")
        void resetPassword_hasSuperAdmin_passesAuthz() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());
            assertAuthzPassed(post("/api/v1/users/1/reset-password")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RESET_BODY));
        }

        // BulkStatusRequest(userIds, targetStatus) — 필드명 정확 일치 필요 (@Validated).
        private static final String BULK_BODY =
                "{\"userIds\":[1,2],\"targetStatus\":\"ACTIVE\"}";

        @Test
        @DisplayName("AC-AME5-D1-4: PATCH /api/v1/users/bulk-status — Authorization 부재 + 401")
        void bulkStatus_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/users/bulk-status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BULK_BODY))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-D1-5: PATCH /api/v1/users/bulk-status — USER 역할 + 403")
        void bulkStatus_missingRole_returns403() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            mockMvc.perform(patch("/api/v1/users/bulk-status")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BULK_BODY))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-AME5-D1-6: PATCH /api/v1/users/bulk-status — DEPT_ADMIN 보유 + 401/403 아님")
        void bulkStatus_hasRole_passesAuthz() throws Exception {
            givenValidToken(Set.of("DEPT_ADMIN"), Set.of());
            assertAuthzPassed(patch("/api/v1/users/bulk-status")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(BULK_BODY));
        }
    }

    // =================================================================================
    // §E DashboardPreferenceDomain — isAuthenticated 6 endpoint (preference 5 + layouts 1)
    // =================================================================================

    @Nested
    @DisplayName("§E.1 DashboardPreferenceDomainTests (dashboard/preference + layouts isAuthenticated 6 endpoint)")
    class DashboardPreferenceDomainTests {

        @Test
        @DisplayName("AC-AME5-E1-1: GET /api/v1/dashboard/preference — Authorization 부재 + 401")
        void prefGet_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard/preference"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-E1-2: GET /api/v1/dashboard/preference — 인증 보유 + 401/403 아님")
        void prefGet_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(get("/api/v1/dashboard/preference")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        @Test
        @DisplayName("AC-AME5-E1-3: PATCH /api/v1/dashboard/preference — Authorization 부재 + 401")
        void prefPatch_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/dashboard/preference")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-E1-4: PATCH /api/v1/dashboard/preference — 인증 보유 + 401/403 아님")
        void prefPatch_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(patch("/api/v1/dashboard/preference")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"));
        }

        @Test
        @DisplayName("AC-AME5-E1-5: POST /api/v1/dashboard/preference/reset — Authorization 부재 + 401")
        void prefReset_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/dashboard/preference/reset"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-E1-6: POST /api/v1/dashboard/preference/reset — 인증 보유 + 401/403 아님")
        void prefReset_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(post("/api/v1/dashboard/preference/reset")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        @Test
        @DisplayName("AC-AME5-E1-7: PATCH /api/v1/dashboard/preference/widgets/{id}/hidden — Authorization 부재 + 401")
        void prefHidden_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/dashboard/preference/widgets/1/hidden")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-E1-8: PATCH /api/v1/dashboard/preference/widgets/{id}/hidden — 인증 보유 + 401/403 아님")
        void prefHidden_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(patch("/api/v1/dashboard/preference/widgets/1/hidden")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"));
        }

        @Test
        @DisplayName("AC-AME5-E1-9: POST /api/v1/dashboard/preference/widgets/{id}/show-all — Authorization 부재 + 401")
        void prefShowAll_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/dashboard/preference/widgets/1/show-all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-E1-10: POST /api/v1/dashboard/preference/widgets/{id}/show-all — 인증 보유 + 401/403 아님")
        void prefShowAll_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(post("/api/v1/dashboard/preference/widgets/1/show-all")
                    .header("Authorization", "Bearer " + VALID_TOKEN));
        }

        @Test
        @DisplayName("AC-AME5-E1-11: PATCH /api/v1/dashboard/layouts/{id}/positions — Authorization 부재 + 401")
        void layoutPositions_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/dashboard/layouts/1/positions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-AME5-E1-12: PATCH /api/v1/dashboard/layouts/{id}/positions — 인증 보유 + 401/403 아님")
        void layoutPositions_authenticated_passesAuthz() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());
            assertAuthzPassed(patch("/api/v1/dashboard/layouts/1/positions")
                    .header("Authorization", "Bearer " + VALID_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"));
        }
    }
}
