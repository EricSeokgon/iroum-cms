package kr.co.ircp.cms.security;

import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.auth.util.HashUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig + JwtAuthenticationFilter 통합 테스트 (Step 3 REFACTOR).
 *
 * <p>Testcontainers PostgreSQL 컨테이너 기반 — 실제 Spring Security 필터 체인에서
 * 인증·미인증·블랙리스트 시나리오를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("SecurityConfig 통합 테스트")
class SecurityConfigIntegrationTest {

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
    }

    @Autowired
    MockMvc mockMvc;

    // JwtTokenProvider, TokenBlacklistMapper는 Mock으로 대체하여
    // DB 토큰 저장 없이 필터 시나리오를 검증
    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    TokenBlacklistMapper tokenBlacklistMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String BLACKLISTED_TOKEN = "blacklisted.jwt.token";
    private static final String EXPIRED_TOKEN = "expired.jwt.token";

    // ─── Public 엔드포인트 ─────────────────────────────────────────────────

    @Test
    @DisplayName("헬스 체크 엔드포인트는 토큰 없이 200 반환")
    void publicEndpoint_returns200_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    // ─── Protected 엔드포인트 ──────────────────────────────────────────────

    @Test
    @DisplayName("토큰 없이 보호된 엔드포인트 요청 → 401 AUTH_REQUIRED")
    void protectedEndpoint_returns401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/protected-any"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("유효한 Bearer 토큰으로 요청 → 404 (엔드포인트 미존재, 하지만 401 아님)")
    void protectedEndpoint_notForbidden_withValidToken() throws Exception {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "testuser", Set.of("EDITOR"), Instant.now().plusSeconds(900));

        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));

        // 엔드포인트가 없으므로 404이지만, 인증은 통과 (401/403 아님)
        mockMvc.perform(get("/api/v1/protected-any")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("블랙리스트 토큰 → 401 TOKEN_REVOKED")
    void protectedEndpoint_returns401_withBlacklistedToken() throws Exception {
        when(tokenBlacklistMapper.exists(HashUtil.sha256Hex(BLACKLISTED_TOKEN))).thenReturn(true);

        mockMvc.perform(get("/api/v1/any")
                        .header("Authorization", "Bearer " + BLACKLISTED_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("만료된 토큰 → 401 TOKEN_EXPIRED")
    void protectedEndpoint_returns401_withExpiredToken() throws Exception {
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(EXPIRED_TOKEN))
                .thenThrow(new TokenExpiredException());

        mockMvc.perform(get("/api/v1/any")
                        .header("Authorization", "Bearer " + EXPIRED_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }
}
