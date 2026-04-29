package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider RED 단계 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001/002 — 모든 테스트는 UnsupportedOperationException으로 실패해야 한다 (RED 의도).
 */
// @MX:TODO: [AUTO] Step 2 GREEN — UOE를 실제 동작 검증으로 교체
@ExtendWith(SpringExtension.class)
@DisplayName("JwtTokenProvider RED 단계 테스트")
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "changeme-256-bits-min-replace-in-prod-aaaaaaaaaaaaaaaaaaaaa",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "iroum-cms"
        );
        jwtTokenProvider = new JwtTokenProviderImpl();
    }

    @Test
    @DisplayName("generateAccessToken — 유효한 JWS 반환 (RED: UOE)")
    void generateAccessToken_returnsValidJws() {
        // RED — UnsupportedOperationException 발생 의도
        assertThatThrownBy(() ->
                jwtTokenProvider.generateAccessToken(1L, "admin", Set.of("SUPER_ADMIN"))
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("generateAccessToken — userId/username/roles 클레임 포함 (RED: UOE)")
    void generateAccessToken_includesUserAndRoles() {
        assertThatThrownBy(() ->
                jwtTokenProvider.generateAccessToken(42L, "editor", Set.of("EDITOR", "VIEWER"))
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("validateAccessToken — 유효한 토큰에서 클레임 반환 (RED: UOE)")
    void validateAccessToken_returnsClaims_whenValid() {
        assertThatThrownBy(() ->
                jwtTokenProvider.validateAccessToken("valid.jwt.token")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("validateAccessToken — 만료된 토큰에서 TokenExpiredException 발생 (RED: UOE)")
    void validateAccessToken_throwsTokenExpired_whenPastExp() {
        assertThatThrownBy(() ->
                jwtTokenProvider.validateAccessToken("expired.jwt.token")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("validateAccessToken — 서명 불일치 시 empty 반환 (RED: UOE)")
    void validateAccessToken_returnsEmpty_whenSignatureMismatch() {
        assertThatThrownBy(() ->
                jwtTokenProvider.validateAccessToken("tampered.jwt.token")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("generateRefreshToken — userId만 포함한 토큰 생성 (RED: UOE)")
    void generateRefreshToken_includesOnlyUserId() {
        assertThatThrownBy(() ->
                jwtTokenProvider.generateRefreshToken(1L)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("extractUserId — 유효한 Refresh Token에서 userId 추출 (RED: UOE)")
    void extractUserId_returnsValidId() {
        assertThatThrownBy(() ->
                jwtTokenProvider.extractUserId("valid.refresh.token")
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
