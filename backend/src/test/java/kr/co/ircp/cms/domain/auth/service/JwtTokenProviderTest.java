package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 행동 검증 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001/002 — 구현이 올바르면 모두 GREEN.
 * Spring 컨텍스트 없이 단위 테스트로 실행.
 */
@DisplayName("JwtTokenProvider 행동 검증 테스트")
class JwtTokenProviderTest {

    private static final String SECRET_64BYTE =
            "test-secret-key-256-bits-long-please-replace-in-production-env-vars";
    private static final String ISSUER = "iroum-cms-test";

    private JwtTokenProvider provider;
    private JwtTokenProvider expiredProvider;

    @BeforeEach
    void setUp() {
        // 정상 TTL (15분 / 7일)
        JwtProperties props = new JwtProperties(
                SECRET_64BYTE,
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                ISSUER
        );
        provider = new JwtTokenProviderImpl(props);

        // 만료 시뮬레이션용 (음수 TTL → 발급 즉시 exp가 과거)
        JwtProperties expiredProps = new JwtProperties(
                SECRET_64BYTE,
                Duration.ofMinutes(-15),
                Duration.ofDays(-1),
                ISSUER
        );
        expiredProvider = new JwtTokenProviderImpl(expiredProps);
    }

    @Test
    @DisplayName("REQ-AUTH-001: Access Token은 JWS 3-segment 형식")
    void generateAccessToken_returnsValidJws() {
        String token = provider.generateAccessToken(1L, "admin", Set.of("SUPER_ADMIN"));
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("REQ-AUTH-001: Access Token claims 포함 검증 (uid, sub, roles)")
    void generateAccessToken_includesUserAndRoles() {
        String token = provider.generateAccessToken(42L, "alice", Set.of("EDITOR", "VIEWER"));
        var claims = provider.validateAccessToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(42L);
        assertThat(claims.get().username()).isEqualTo("alice");
        assertThat(claims.get().roles()).containsExactlyInAnyOrder("EDITOR", "VIEWER");
    }

    @Test
    @DisplayName("REQ-AUTH-001: 정상 토큰 검증 시 클레임 반환 및 만료 시각이 미래")
    void validateAccessToken_returnsClaims_whenValid() {
        String token = provider.generateAccessToken(1L, "admin", Set.of());
        var claims = provider.validateAccessToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().expiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("REQ-AUTH-002: 만료된 토큰은 TokenExpiredException 발생")
    void validateAccessToken_throwsTokenExpired_whenPastExp() {
        String expired = expiredProvider.generateAccessToken(1L, "admin", Set.of());
        assertThatThrownBy(() -> provider.validateAccessToken(expired))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("REQ-AUTH-001: 다른 키로 서명된 토큰은 Optional.empty 반환")
    void validateAccessToken_returnsEmpty_whenSignatureMismatch() {
        // 다른 secret으로 서명된 토큰 생성
        JwtProperties differentSecret = new JwtProperties(
                "different-secret-256-bits-long-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                ISSUER
        );
        JwtTokenProvider other = new JwtTokenProviderImpl(differentSecret);
        String foreign = other.generateAccessToken(1L, "intruder", Set.of());

        assertThat(provider.validateAccessToken(foreign)).isEmpty();
    }

    @Test
    @DisplayName("REQ-AUTH-002: Refresh Token은 userId만 포함 (sub=userId, roles 없음)")
    void generateRefreshToken_includesOnlyUserId() {
        String refresh = provider.generateRefreshToken(99L);
        assertThat(refresh).isNotBlank();
        assertThat(refresh.split("\\.")).hasSize(3);
        // Refresh Token에서 userId 추출이 정상 동작
        assertThat(provider.extractUserId(refresh)).contains(99L);
    }

    @Test
    @DisplayName("REQ-AUTH-002: extractUserId — Refresh Token에서 정확한 userId 반환")
    void extractUserId_returnsValidId() {
        String refresh = provider.generateRefreshToken(12345L);
        assertThat(provider.extractUserId(refresh)).contains(12345L);
    }

    @Test
    @DisplayName("REQ-AUTH-013: Access Token 생성 시 permissions 클레임 포함")
    void generateAccessToken_includesPermissions() {
        Set<String> permissions = Set.of("USER:READ", "USER:WRITE");
        String token = provider.generateAccessToken(10L, "manager", Set.of("DEPT_ADMIN"), permissions);

        var claims = provider.validateAccessToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().permissions()).containsExactlyInAnyOrder("USER:READ", "USER:WRITE");
    }

    @Test
    @DisplayName("REQ-AUTH-013: validateAccessToken — permissions 클레임 정상 추출")
    void validateAccessToken_returnsPermissionsInClaims() {
        Set<String> perms = Set.of("ORGANIZATION:READ", "ROLE:READ", "AUDIT:READ");
        String token = provider.generateAccessToken(20L, "editor", Set.of("EDITOR"), perms);

        var claims = provider.validateAccessToken(token);
        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(20L);
        assertThat(claims.get().roles()).containsExactly("EDITOR");
        assertThat(claims.get().permissions()).containsExactlyInAnyOrder(
                "ORGANIZATION:READ", "ROLE:READ", "AUDIT:READ");
    }
}
