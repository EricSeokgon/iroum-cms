package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthService RED 단계 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003, 005, 011 — 모든 테스트는 UnsupportedOperationException으로 실패해야 한다 (RED 의도).
 * 실제 로직은 Step 2 GREEN에서 구현된다.
 */
// @MX:TODO: [AUTO] Step 2 GREEN — 로그인/토큰/로그아웃 전체 흐름 구현 후 테스트 갱신
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService RED 단계 테스트")
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private LoginHistoryMapper loginHistoryMapper;
    @Mock private TokenBlacklistMapper tokenBlacklistMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordPolicyService passwordPolicyService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userMapper, refreshTokenMapper, loginHistoryMapper,
                tokenBlacklistMapper, jwtTokenProvider, passwordPolicyService);
    }

    @Test
    @DisplayName("login — 유효한 자격증명으로 토큰 반환 (RED: UOE)")
    void login_returnsTokens_whenCredentialsValid() {
        var req = new LoginRequest("admin", "ValidP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 사용자 미존재 시 InvalidCredentialsException (RED: UOE)")
    void login_throwsInvalidCredentials_whenUserNotFound() {
        var req = new LoginRequest("ghost", "ValidP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 비밀번호 불일치 시 InvalidCredentialsException (RED: UOE)")
    void login_throwsInvalidCredentials_whenPasswordMismatch() {
        var req = new LoginRequest("admin", "WrongP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 비밀번호 불일치 시 fail_count 증가 (RED: UOE)")
    void login_incrementsFailCount_whenInvalid() {
        var req = new LoginRequest("admin", "WrongP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 5회 실패 후 계정 잠금 (RED: UOE)")
    void login_locksAccount_after5Failures() {
        var req = new LoginRequest("admin", "WrongP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 잠긴 계정 시도 시 AccountLockedException (RED: UOE)")
    void login_throwsAccountLocked_whenLocked() {
        var req = new LoginRequest("locked", "ValidP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 성공 시 login_history 기록 (RED: UOE)")
    void login_recordsHistory_onSuccess() {
        var req = new LoginRequest("admin", "ValidP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("login — 실패 시 login_history 기록 (RED: UOE)")
    void login_recordsHistory_onFailure() {
        var req = new LoginRequest("admin", "WrongP@ss123");
        assertThatThrownBy(() ->
                authService.login(req, "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("refresh — 새 토큰 쌍 반환 (Rotation) (RED: UOE)")
    void refresh_rotatesAndReturnsNewTokens() {
        assertThatThrownBy(() ->
                authService.refresh("valid.refresh.token", "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("refresh — 만료된 토큰 시 TokenExpiredException (RED: UOE)")
    void refresh_throwsTokenExpired_whenStale() {
        assertThatThrownBy(() ->
                authService.refresh("expired.refresh.token", "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("refresh — 이미 회수된 토큰 재사용 시 TokenReuseException (RED: UOE)")
    void refresh_throwsTokenReuse_whenAlreadyRevoked() {
        assertThatThrownBy(() ->
                authService.refresh("revoked.refresh.token", "127.0.0.1", "TestAgent")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("logout — Refresh Token 회수 (RED: UOE)")
    void logout_revokesRefreshToken() {
        assertThatThrownBy(() ->
                authService.logout("Bearer access.token", "refresh.token")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("logout — Access Token 블랙리스트 등록 (RED: UOE)")
    void logout_addsAccessToBlacklist() {
        assertThatThrownBy(() ->
                authService.logout("Bearer access.token", "refresh.token")
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
