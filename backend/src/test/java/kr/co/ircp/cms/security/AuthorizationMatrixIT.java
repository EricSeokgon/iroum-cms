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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 RUN — HTTP 권한 매트릭스 통합 테스트.
 *
 * <p>운영 SecurityFilterChain + JwtAuthenticationFilter + Method Security를
 * {@link SpringBootTest}로 그대로 적재하여 회귀 검출 능력을 확보한다.
 *
 * <p>{@link JwtTokenProvider} / {@link TokenBlacklistMapper}는 {@code @MockitoBean}으로 우회하여
 * DB 토큰 저장 없이 시나리오만 검증한다. PII 더미 키는 PII-001 인프라 패턴과 일관되게 주입한다.
 *
 * <p>운영 코드 변경은 0건이며, 본 IT는 테스트 추가 위주이다.
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>§A AC-AM-001-1/2/3 — 인프라 신설 + smoke test (3건)</li>
 *   <li>§B AC-AM-002-1~12 — WRITE 권한 endpoint 매트릭스 (6 endpoint × 2 시나리오 = 12건)</li>
 *   <li>§C AC-AM-003-1~4 — 401/403 응답 body 회귀 + 운영 인터셉터 적재 검증 (4건)</li>
 * </ul>
 *
 * <p>관련 SPEC: SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 (REQ-AUTHZ-MATRIX-001/002/003)
 */
// @MX:NOTE: [AUTO] AuthorizationMatrixIT — 운영 SecurityFilterChain @PreAuthorize 회귀 검출 IT
// @MX:SPEC: SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("HTTP 권한 매트릭스 통합 테스트 (SPEC-CMS-SECURITY-AUTHZ-MATRIX-001)")
class AuthorizationMatrixIT {

    // ─── Testcontainers PostgreSQL 16 (운영 동등 Flyway 마이그레이션 적용) ──────────────
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
        // PII 더미 키 (32 bytes base64) — PII-001 인프라 일관
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

    // ─── JWT Mock helper ─────────────────────────────────────────────────────────
    /**
     * 주어진 roles/permissions로 valid 토큰을 시뮬레이션한다.
     *
     * <p>Mock된 {@link JwtTokenProvider#validateAccessToken(String)}이 {@link JwtTokenProvider.JwtClaims}
     * 를 반환하도록 stub하여 운영 {@code JwtAuthenticationFilter}가 SecurityContext에 인증 principal을
     * 설정하게 한다. {@link TokenBlacklistMapper#exists(String)}은 항상 false 반환.
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
    // §A REQ-AUTHZ-MATRIX-001 — IT 인프라 신설 + smoke test
    // =================================================================================
    @Nested
    @DisplayName("§A REQ-AUTHZ-MATRIX-001 — IT 인프라 + smoke test")
    class InfrastructureTests {

        /**
         * AC-AM-001-1: 컨텍스트 부팅 + JWT Mock 주입 정상.
         *
         * <p>본 테스트가 실행 가능하다는 것 자체가 컨텍스트 로드 + Mock 주입 GREEN을 의미한다.
         * MockMvc/JwtTokenProvider/TokenBlacklistMapper Bean이 주입되었음을 단순 검증한다.
         */
        @Test
        @DisplayName("AC-AM-001-1: Spring 컨텍스트 부팅 + JwtTokenProvider/TokenBlacklistMapper Mock 주입")
        void contextLoadsWithMockedJwtBeans() {
            // Given/When/Then: 컨텍스트가 부팅되어 본 메소드 진입한 시점에 모든 Bean이 주입된 상태
            assertNotNull(mockMvc, "MockMvc 주입 확인");
            assertNotNull(jwtTokenProvider, "JwtTokenProvider Mock 주입 확인");
            assertNotNull(tokenBlacklistMapper, "TokenBlacklistMapper Mock 주입 확인");
        }

        /**
         * AC-AM-001-2: public endpoint smoke test — 인증 미요구 경로는 토큰 없이 200 반환.
         *
         * <p>운영 SecurityConfig의 {@code requestMatchers("/api/v1/health/**").permitAll()} 정책 검증.
         */
        @Test
        @DisplayName("AC-AM-001-2: public endpoint(/api/v1/health) — Authorization 헤더 부재 + 200 OK")
        void publicEndpoint_returns200_withoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isOk());
        }

        /**
         * AC-AM-001-3: 보호 endpoint smoke test — Authorization 헤더 부재 → 401 AUTH_REQUIRED.
         *
         * <p>운영 {@code SecurityConfig.anyRequest().authenticated()} + {@code authenticationEntryPoint}
         * (라인 113~116) 정책 검증.
         */
        @Test
        @DisplayName("AC-AM-001-3: 보호 endpoint(POST /api/v1/content/banners) — 토큰 부재 + 401 AUTH_REQUIRED")
        void protectedEndpoint_returns401_withoutToken() throws Exception {
            mockMvc.perform(post("/api/v1/content/banners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }
    }

    // =================================================================================
    // §B REQ-AUTHZ-MATRIX-002 — WRITE 권한 endpoint 매트릭스
    // 6 endpoint × 2 시나리오(권한 부족 403 / 정합 권한 2xx) = 12 AC
    // =================================================================================
    @Nested
    @DisplayName("§B REQ-AUTHZ-MATRIX-002 — WRITE 권한 endpoint 매트릭스 (12건)")
    class AuthorizationMatrixTests {

        // ─── Banner POST (CONTENT:WRITE) ─────────────────────────────────────────

        /**
         * AC-AM-002-1: Banner POST 권한 부족 → 403 AUTH_FORBIDDEN.
         * USER 역할 토큰은 CONTENT:WRITE permission 미보유 → @PreAuthorize 미충족.
         */
        @Test
        @DisplayName("AC-AM-002-1: POST /api/v1/content/banners — CONTENT:WRITE 부재 + 403")
        void bannerCreate_forbidden_whenContentWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // CONTENT:WRITE 미보유

            mockMvc.perform(post("/api/v1/content/banners")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /**
         * AC-AM-002-2: Banner POST 정합 권한 → 401/403 외 status(권한 통과).
         * EDITOR 역할 + CONTENT:WRITE permission 보유 → @PreAuthorize 통과.
         * Body 부적절로 인한 400/422도 권한 게이트 통과 신호로 GREEN 처리(AC 정의).
         */
        @Test
        @DisplayName("AC-AM-002-2: POST /api/v1/content/banners — CONTENT:WRITE 보유 + 401/403 아님")
        void bannerCreate_passesAuthorization_whenContentWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));

            mockMvc.perform(post("/api/v1/content/banners")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── Banner PUT (CONTENT:WRITE) ──────────────────────────────────────────

        /**
         * AC-AM-002-3: Banner PUT 권한 부족 → 403 AUTH_FORBIDDEN.
         */
        @Test
        @DisplayName("AC-AM-002-3: PUT /api/v1/content/banners/{id} — CONTENT:WRITE 부재 + 403")
        void bannerUpdate_forbidden_whenContentWriteMissing() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(put("/api/v1/content/banners/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /**
         * AC-AM-002-4: Banner PUT 정합 권한 → 401/403 외 status(권한 통과).
         * 존재하지 않는 banner id로 인한 404도 GREEN(AC 정의).
         */
        @Test
        @DisplayName("AC-AM-002-4: PUT /api/v1/content/banners/{id} — CONTENT:WRITE 보유 + 401/403 아님")
        void bannerUpdate_passesAuthorization_whenContentWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE"));

            mockMvc.perform(put("/api/v1/content/banners/1")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── Page POST (PAGE:WRITE) ──────────────────────────────────────────────

        /**
         * AC-AM-002-5: Page POST 권한 부족 → 403 AUTH_FORBIDDEN.
         * CONTENT:WRITE 보유하더라도 PAGE:WRITE 부재 시 정책 미충족(별개 권한 어휘 회귀 검증).
         */
        @Test
        @DisplayName("AC-AM-002-5: POST /api/v1/content/pages — PAGE:WRITE 부재(CONTENT:WRITE만 보유) + 403")
        void pageCreate_forbidden_whenPageWriteMissing() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("CONTENT:WRITE")); // PAGE:WRITE는 부재

            mockMvc.perform(post("/api/v1/content/pages")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /**
         * AC-AM-002-6: Page POST 정합 권한 → 401/403 외 status(권한 통과).
         */
        @Test
        @DisplayName("AC-AM-002-6: POST /api/v1/content/pages — PAGE:WRITE 보유 + 401/403 아님")
        void pageCreate_passesAuthorization_whenPageWritePresent() throws Exception {
            givenValidToken(Set.of("EDITOR"), Set.of("PAGE:WRITE"));

            mockMvc.perform(post("/api/v1/content/pages")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── CacheAdmin invalidate (hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')) ─────

        /**
         * AC-AM-002-7: CacheAdmin invalidate 권한 부족 → 403 AUTH_FORBIDDEN.
         * USER 역할은 SUPER_ADMIN/DEPT_ADMIN 어느 것도 아님 → 정책 미충족.
         */
        @Test
        @DisplayName("AC-AM-002-7: POST /api/v1/dashboard/cache/invalidate — USER 역할 + 403")
        void cacheInvalidate_forbidden_whenNotAdminRole() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /**
         * AC-AM-002-8: CacheAdmin invalidate 정합 권한 → 401/403 외 status.
         * SUPER_ADMIN 역할 → JwtPrincipal#getAuthorities()가 ROLE_SUPER_ADMIN로 변환 → hasAnyRole 통과.
         */
        @Test
        @DisplayName("AC-AM-002-8: POST /api/v1/dashboard/cache/invalidate — SUPER_ADMIN 역할 + 401/403 아님")
        void cacheInvalidate_passesAuthorization_whenSuperAdminRole() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/dashboard/cache/invalidate")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── User POST (hasRole('SUPER_ADMIN')) ──────────────────────────────────

        /**
         * AC-AM-002-9: User 등록 권한 부족 → 403 AUTH_FORBIDDEN.
         * ADMIN 역할은 SUPER_ADMIN 정책을 충족하지 않음(역할 위계 회귀).
         */
        @Test
        @DisplayName("AC-AM-002-9: POST /api/v1/users — ADMIN 역할(SUPER_ADMIN 미충족) + 403")
        void userCreate_forbidden_whenNotSuperAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /**
         * AC-AM-002-10: User 등록 정합 권한 → 401/403 외 status.
         */
        @Test
        @DisplayName("AC-AM-002-10: POST /api/v1/users — SUPER_ADMIN 역할 + 401/403 아님")
        void userCreate_passesAuthorization_whenSuperAdmin() throws Exception {
            givenValidToken(Set.of("SUPER_ADMIN"), Set.of());

            mockMvc.perform(post("/api/v1/users")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }

        // ─── Governance class-level (hasRole('ADMIN')) ───────────────────────────

        /**
         * AC-AM-002-11: Governance 클래스 레벨 ADMIN 권한 부족 → 403 AUTH_FORBIDDEN.
         * 클래스 레벨 @PreAuthorize 운영 적재 검증.
         */
        @Test
        @DisplayName("AC-AM-002-11: GET /api/v1/governance/retention-policies — USER 역할 + 403")
        void governance_forbidden_whenNotAdmin() throws Exception {
            givenValidToken(Set.of("USER"), Set.of());

            mockMvc.perform(get("/api/v1/governance/retention-policies")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }

        /**
         * AC-AM-002-12: Governance 클래스 레벨 ADMIN 정합 권한 → 401/403 외 status.
         */
        @Test
        @DisplayName("AC-AM-002-12: GET /api/v1/governance/retention-policies — ADMIN 역할 + 401/403 아님")
        void governance_passesAuthorization_whenAdmin() throws Exception {
            givenValidToken(Set.of("ADMIN"), Set.of());

            mockMvc.perform(get("/api/v1/governance/retention-policies")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().is(not(equalTo(401))))
                    .andExpect(status().is(not(equalTo(403))));
        }
    }

    // =================================================================================
    // §C REQ-AUTHZ-MATRIX-003 — 401/403 응답 body 회귀 + 운영 인터셉터 적재 검증
    // =================================================================================
    @Nested
    @DisplayName("§C REQ-AUTHZ-MATRIX-003 — 응답 body 회귀 + 인터셉터 적재 검증")
    class ResponseBodyRegressionTests {

        /**
         * AC-AM-003-1: 401 응답 body 회귀 검증 — code=AUTH_REQUIRED + Content-Type + jsonPath.
         * 운영 SecurityConfig.authenticationEntryPoint(라인 113~116) 응답 형식 회귀 기준선 고정.
         */
        @Test
        @DisplayName("AC-AM-003-1: 401 응답 body — code=AUTH_REQUIRED + application/json;charset=UTF-8 + message 존재")
        void unauthorizedResponse_bodyContract() throws Exception {
            mockMvc.perform(post("/api/v1/content/banners")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType("application/json;charset=UTF-8"))
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                    .andExpect(jsonPath("$.message").exists());
        }

        /**
         * AC-AM-003-2: 403 응답 body 회귀 검증 — code=AUTH_FORBIDDEN + Content-Type + jsonPath.
         * 운영 SecurityConfig.accessDeniedHandler(라인 121~123) 응답 형식 회귀 기준선 고정.
         */
        @Test
        @DisplayName("AC-AM-003-2: 403 응답 body — code=AUTH_FORBIDDEN + application/json;charset=UTF-8 + message 존재")
        void forbiddenResponse_bodyContract() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // CONTENT:WRITE 부재

            mockMvc.perform(post("/api/v1/content/banners")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType("application/json;charset=UTF-8"))
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"))
                    .andExpect(jsonPath("$.message").exists());
        }

        /**
         * AC-AM-003-3: JwtAuthenticationFilter 운영 적재 검증 — anonymous 흐름 → EntryPoint 도달.
         *
         * <p>Authorization 헤더 부재 시 JwtAuthenticationFilter가 인증 미설정 → ExceptionTranslationFilter
         * → authenticationEntryPoint 호출 경로가 정상 작동함을 401 + AUTH_REQUIRED로 간접 검증.
         * 운영 chain에 JwtAuthenticationFilter가 정상 적재되었음을 의미.
         */
        @Test
        @DisplayName("AC-AM-003-3: JwtAuthenticationFilter 운영 적재 — anonymous → 401 EntryPoint 경로 GREEN")
        void jwtAuthenticationFilter_isLoaded_anonymousReachesEntryPoint() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        }

        /**
         * AC-AM-003-4: Method Security 운영 인터셉터 적재 검증 — @PreAuthorize → AccessDenied 경로.
         *
         * <p>유효 토큰 + 권한 부족 상태에서 @PreAuthorize가 AccessDeniedException을 throw하고
         * accessDeniedHandler가 응답을 작성하는 경로가 정상 작동함을 403 + AUTH_FORBIDDEN로 간접 검증.
         * SecurityConfig의 @EnableMethodSecurity 적용 회귀 검출.
         */
        @Test
        @DisplayName("AC-AM-003-4: Method Security 운영 인터셉터 — @PreAuthorize → 403 AccessDenied 경로 GREEN")
        void methodSecurity_isLoaded_preAuthorizeBlocksInsufficientAuthority() throws Exception {
            givenValidToken(Set.of("USER"), Set.of()); // CONTENT:WRITE 부재

            mockMvc.perform(post("/api/v1/content/banners")
                            .header("Authorization", "Bearer " + VALID_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        }
    }

}
