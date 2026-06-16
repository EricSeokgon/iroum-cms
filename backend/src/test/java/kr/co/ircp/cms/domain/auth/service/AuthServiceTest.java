package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.entity.RefreshToken;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.auth.entity.VerificationRequest;
import kr.co.ircp.cms.domain.auth.entity.VerificationStatus;
import kr.co.ircp.cms.domain.auth.exception.AccountLockedException;
import kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException;
import kr.co.ircp.cms.domain.auth.exception.InvalidVerifiedTokenException;
import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import kr.co.ircp.cms.domain.auth.exception.PasswordReuseException;
import kr.co.ircp.cms.domain.auth.exception.TokenExpiredException;
import kr.co.ircp.cms.domain.auth.exception.TokenReuseException;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.PasswordHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~003, 005, 011 — 실제 로직을 Mockito로 검증한다.
 */
// @MX:NOTE: [AUTO] Step 2 GREEN — 모든 UOE matcher 제거, 동작 검증(verify) 기반으로 전환
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService GREEN 단계 테스트")
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private RefreshTokenMapper refreshTokenMapper;
    @Mock private LoginHistoryMapper loginHistoryMapper;
    @Mock private TokenBlacklistMapper tokenBlacklistMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private PasswordHistoryMapper passwordHistoryMapper;
    @Mock private PermissionService permissionService;
    @Mock private VerificationService verificationService;
    @Mock private EmailService emailService;
    @Mock private kr.co.ircp.cms.domain.security.pii.EmailEncryptionService emailEncryptionService;
    @Mock private kr.co.ircp.cms.domain.system.setting.service.SystemSettingService systemSettingService;

    private JwtProperties jwtProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "test-secret-256-bits-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "iroum-cms-test");
        authService = new AuthServiceImpl(
                userMapper, refreshTokenMapper, loginHistoryMapper,
                tokenBlacklistMapper, jwtTokenProvider, passwordPolicyService,
                passwordHistoryMapper, jwtProperties, permissionService,
                verificationService, emailService, emailEncryptionService,
                systemSettingService);
        // SPEC-CMS-SECURITY-PII-001 — password reset flow 가 호출하는 HMAC 격상 stub.
        org.mockito.Mockito.lenient().when(emailEncryptionService.computeHmac(anyString()))
                .thenAnswer(inv -> "hmac-of-" + inv.getArgument(0).toString().toLowerCase());
    }

    // ============================================================
    // REQ-AUTH-001: 로그인
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-001: 정상 로그인 시 Access Token 발급 + Refresh 저장 + 이력 기록")
    void login_returnsTokens_whenCredentialsValid() {
        User user = activeUser(1L, "admin", 0);
        when(userMapper.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("ValidP@ss123", user.getPasswordHash())).thenReturn(true);
        when(userMapper.findRoleCodesByUserId(1L)).thenReturn(Set.of("SUPER_ADMIN"));
        when(permissionService.findEffectivePermissionsForUser(1L)).thenReturn(Set.of("USER:READ"));
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("admin"), any(Set.class), any(Set.class)))
                .thenReturn("access-jwt");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-jwt");

        AuthService.LoginOutcome outcome = authService.login(
                new LoginRequest("admin", "ValidP@ss123"), "127.0.0.1", "JUnit");

        assertThat(outcome.response().accessToken()).isEqualTo("access-jwt");
        assertThat(outcome.refreshToken()).isEqualTo("refresh-jwt");
        assertThat(outcome.response().tokenType()).isEqualTo("Bearer");
        assertThat(outcome.response().expiresInSeconds()).isEqualTo(900L);

        verify(userMapper).resetFailCount(eq("admin"), any(Instant.class));
        verify(userMapper).updateLastLoginAt(eq(1L), any(Instant.class));
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
        verify(loginHistoryMapper).insert(argThat(h -> h.isSuccess() && "admin".equals(h.getUsername())));
    }

    @Test
    @DisplayName("REQ-AUTH-001: 사용자 없음 → InvalidCredentials + 실패이력(USER_NOT_FOUND)")
    void login_throwsInvalidCredentials_whenUserNotFound() {
        when(userMapper.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("ghost", "x"), "127.0.0.1", "ua"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginHistoryMapper).insert(argThat(h ->
                !h.isSuccess() && "USER_NOT_FOUND".equals(h.getFailureReason())));
        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("REQ-AUTH-001: 비밀번호 불일치 시 InvalidCredentials + incrementFailCount 호출")
    void login_throwsInvalidCredentials_whenPasswordMismatch() {
        User user = activeUser(2L, "admin", 1);
        when(userMapper.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("WrongP@ss123", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("admin", "WrongP@ss123"), "127.0.0.1", "ua"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userMapper).incrementFailCount(eq("admin"), any(Instant.class));
        verify(loginHistoryMapper).insert(argThat(h ->
                !h.isSuccess() && "INVALID_PASSWORD".equals(h.getFailureReason())));
    }

    @Test
    @DisplayName("REQ-AUTH-001: 비밀번호 불일치 시 fail_count 증가 확인 (incrementFailCount)")
    void login_incrementsFailCount_whenInvalid() {
        User user = activeUser(3L, "admin", 2);
        when(userMapper.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("admin", "WrongP@ss123"), "127.0.0.1", "ua"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userMapper).incrementFailCount(eq("admin"), any(Instant.class));
        // 3+1=4, 아직 5 미만이므로 lockAccount는 호출되지 않아야 함
        verify(userMapper, never()).lockAccount(any(), any());
    }

    // ============================================================
    // REQ-AUTH-005: 계정 잠금
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-005: 5회 실패 시 lockAccount 호출")
    void login_locksAccount_after5Failures() {
        // failCount=4 — 이번 시도로 5가 됨
        User user = activeUser(4L, "admin", 4);
        when(userMapper.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("admin", "WrongP@ss123"), "127.0.0.1", "ua"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userMapper).incrementFailCount(eq("admin"), any(Instant.class));
        verify(userMapper).lockAccount(eq("admin"), any(Instant.class));
    }

    @Test
    @DisplayName("REQ-AUTH-005: LOCKED 상태 + lockedUntil > now → AccountLockedException")
    void login_throwsAccountLocked_whenLocked() {
        User user = User.builder()
                .id(5L).username("locked").passwordHash("$2a$12$hash")
                .status(UserStatus.LOCKED)
                .failCount(5)
                .lockedUntil(Instant.now().plusSeconds(1800))
                .build();
        when(userMapper.findByUsername("locked")).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("locked", "ValidP@ss123"), "127.0.0.1", "ua"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordPolicyService, never()).matches(any(), any());
    }

    // ============================================================
    // REQ-AUTH-011: 로그인 이력
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-011: 로그인 성공 → loginHistory(success=true)")
    void login_recordsHistory_onSuccess() {
        User user = activeUser(6L, "admin", 0);
        when(userMapper.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches(any(), any())).thenReturn(true);
        when(userMapper.findRoleCodesByUserId(6L)).thenReturn(Set.of());
        when(permissionService.findEffectivePermissionsForUser(6L)).thenReturn(Set.of());
        when(jwtTokenProvider.generateAccessToken(anyLong(), any(), any(), any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("rt");

        authService.login(new LoginRequest("admin", "ValidP@ss123"), "1.2.3.4", "agent");

        verify(loginHistoryMapper).insert(argThat(h ->
                h.isSuccess()
                && "admin".equals(h.getUsername())
                && "1.2.3.4".equals(h.getIpAddress())
                && "agent".equals(h.getUserAgent())));
    }

    @Test
    @DisplayName("REQ-AUTH-011: 로그인 실패 → loginHistory(success=false, failureReason)")
    void login_recordsHistory_onFailure() {
        when(userMapper.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("nobody", "pw"), "9.9.9.9", "bot"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginHistoryMapper).insert(argThat(h ->
                !h.isSuccess()
                && "nobody".equals(h.getUsername())
                && h.getFailureReason() != null));
    }

    // ============================================================
    // REQ-AUTH-002: Refresh Token Rotation
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-002: refresh 정상 시 토큰 회전 + 이전 토큰 무효화")
    void refresh_rotatesAndReturnsNewTokens() {
        String oldRaw = "old-refresh-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(oldRaw))
                .userId(1L)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(null)
                .build();

        when(jwtTokenProvider.extractUserId(oldRaw)).thenReturn(Optional.of(1L));
        when(refreshTokenMapper.findByTokenHash(sha256Hex(oldRaw))).thenReturn(Optional.of(stored));
        // REQ-AUTH-002 Rotation: refresh 흐름은 최신 권한 반영을 위해 사용자 + 역할/권한 재조회
        User refreshedUser = activeUser(1L, "admin", 0);
        when(userMapper.findById(1L)).thenReturn(Optional.of(refreshedUser));
        when(userMapper.findRoleCodesByUserId(1L)).thenReturn(Set.of("SUPER_ADMIN"));
        when(permissionService.findEffectivePermissionsForUser(1L)).thenReturn(Set.of("USER:READ"));
        // 4-인자 generateAccessToken 시그니처(userId, username, roles, permissions)에 맞춰 스텁
        when(jwtTokenProvider.generateAccessToken(eq(1L), any(), any(Set.class), any(Set.class)))
                .thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new-refresh");

        var result = authService.refresh(oldRaw, "127.0.0.1", "ua");

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.newRefreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenMapper).revoke(eq(sha256Hex(oldRaw)), any(Instant.class));
        verify(refreshTokenMapper).insert(argThat(t -> sha256Hex("new-refresh").equals(t.getTokenHash())));
    }

    @Test
    @DisplayName("REQ-AUTH-002: 만료된 refresh → TokenExpiredException")
    void refresh_throwsTokenExpired_whenStale() {
        String raw = "expired-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .userId(1L)
                .expiresAt(Instant.now().minusSeconds(1))  // 이미 만료
                .revokedAt(null)
                .build();

        when(jwtTokenProvider.extractUserId(raw)).thenReturn(Optional.of(1L));
        when(refreshTokenMapper.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(raw, "127.0.0.1", "ua"))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("REQ-AUTH-002: revoked 토큰 재사용 → TokenReuseException + 모든 토큰 무효화")
    void refresh_throwsTokenReuse_whenAlreadyRevoked() {
        String raw = "revoked-token";
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256Hex(raw))
                .userId(7L)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revokedAt(Instant.now().minusSeconds(60))  // 이미 회수됨
                .build();

        when(jwtTokenProvider.extractUserId(raw)).thenReturn(Optional.of(7L));
        when(refreshTokenMapper.findByTokenHash(sha256Hex(raw))).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(raw, "127.0.0.1", "ua"))
                .isInstanceOf(TokenReuseException.class);

        // 탈취 감지 시 해당 사용자 전체 토큰 무효화
        verify(refreshTokenMapper).revokeAllForUser(eq(7L), any(Instant.class));
    }

    // ============================================================
    // REQ-AUTH-003: 로그아웃
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-003: logout 시 Refresh Token 회수")
    void logout_revokesRefreshToken() {
        authService.logout("access.token", "refresh.token");

        verify(refreshTokenMapper).revoke(eq(sha256Hex("refresh.token")), any(Instant.class));
    }

    @Test
    @DisplayName("REQ-AUTH-003: logout 시 Access Token 블랙리스트 등록")
    void logout_addsAccessToBlacklist() {
        when(jwtTokenProvider.validateAccessToken("access.token")).thenReturn(Optional.empty());

        authService.logout("access.token", "refresh.token");

        verify(tokenBlacklistMapper).insert(argThat(entry ->
                sha256Hex("access.token").equals(entry.getTokenHash())));
    }

    // ============================================================
    // REQ-AUTH-009 / REQ-AUTH-010: 비밀번호 변경 + 재사용 금지
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-009: 현재 비밀번호 일치 + 새 비밀번호 정책 준수 → 변경 성공")
    void changePassword_succeeds_whenCurrentMatches_andNewIsCompliant() {
        User user = activeUser(10L, "admin", 0);
        when(userMapper.findById(10L)).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("OldP@ss123", user.getPasswordHash())).thenReturn(true);
        when(passwordHistoryMapper.findRecentHashes(10L, 5)).thenReturn(List.of());
        when(passwordPolicyService.matches(eq("NewP@ss456!"), any())).thenReturn(false);
        when(passwordPolicyService.hash("NewP@ss456!")).thenReturn("$2a$12$newHash");

        authService.changePassword(10L, "OldP@ss123", "NewP@ss456!");

        verify(userMapper).updatePassword(eq(10L), eq("$2a$12$newHash"), any(Instant.class));
        verify(passwordHistoryMapper).insert(eq(10L), eq("$2a$12$newHash"), any(Instant.class));
        verify(refreshTokenMapper).revokeAllForUser(eq(10L), any(Instant.class));
    }

    @Test
    @DisplayName("REQ-AUTH-009: 현재 비밀번호 불일치 → InvalidCredentialsException")
    void changePassword_throwsInvalidCredentials_whenCurrentMismatch() {
        User user = activeUser(11L, "admin", 0);
        when(userMapper.findById(11L)).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("WrongOld", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(11L, "WrongOld", "NewP@ss456!"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("REQ-AUTH-009: 새 비밀번호 정책 위반 → PasswordPolicyViolationException")
    void changePassword_throwsPasswordPolicy_whenNewWeak() {
        User user = activeUser(12L, "admin", 0);
        when(userMapper.findById(12L)).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("OldP@ss123", user.getPasswordHash())).thenReturn(true);
        doThrow(new PasswordPolicyViolationException("정책 위반"))
                .when(passwordPolicyService).validate("weak");

        assertThatThrownBy(() -> authService.changePassword(12L, "OldP@ss123", "weak"))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("REQ-AUTH-010: 새 비밀번호가 현재 해시와 동일 → PasswordReuseException")
    void changePassword_throwsPasswordReuse_whenNewMatchesCurrentHash() {
        User user = activeUser(13L, "admin", 0);
        when(userMapper.findById(13L)).thenReturn(Optional.of(user));
        // 현재 비밀번호 검증: OldP@ss123 일치
        when(passwordPolicyService.matches(eq("OldP@ss123"), eq(user.getPasswordHash()))).thenReturn(true);
        when(passwordHistoryMapper.findRecentHashes(13L, 5)).thenReturn(List.of());
        // 새 비번이 현재 해시와 일치 → 재사용 탐지
        when(passwordPolicyService.matches(eq("NewP@ss456!"), eq(user.getPasswordHash()))).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(13L, "OldP@ss123", "NewP@ss456!"))
                .isInstanceOf(PasswordReuseException.class);

        verify(userMapper, never()).updatePassword(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("REQ-AUTH-010: 새 비밀번호가 이력 항목과 일치 → PasswordReuseException")
    void changePassword_throwsPasswordReuse_whenNewMatchesHistoryEntry() {
        User user = activeUser(14L, "admin", 0);
        String historyHash = "$2a$12$oldHistoryHash";
        when(userMapper.findById(14L)).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("OldP@ss123", user.getPasswordHash())).thenReturn(true);
        when(passwordHistoryMapper.findRecentHashes(14L, 5)).thenReturn(List.of(historyHash));
        // 새 비번이 현재 해시와는 다르지만 이력 해시와 일치
        when(passwordPolicyService.matches(eq("NewP@ss456!"), eq(user.getPasswordHash()))).thenReturn(false);
        when(passwordPolicyService.matches(eq("NewP@ss456!"), eq(historyHash))).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(14L, "OldP@ss123", "NewP@ss456!"))
                .isInstanceOf(PasswordReuseException.class);
    }

    @Test
    @DisplayName("REQ-AUTH-009: 변경 성공 시 새 해시가 DB와 이력에 모두 적재됨")
    void changePassword_persistsNewHashAndHistory() {
        User user = activeUser(15L, "admin", 0);
        when(userMapper.findById(15L)).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("OldP@ss123", user.getPasswordHash())).thenReturn(true);
        when(passwordHistoryMapper.findRecentHashes(15L, 5)).thenReturn(List.of());
        when(passwordPolicyService.matches(eq("NewP@ss456!"), any())).thenReturn(false);
        when(passwordPolicyService.hash("NewP@ss456!")).thenReturn("$2a$12$newHash");

        authService.changePassword(15L, "OldP@ss123", "NewP@ss456!");

        verify(userMapper).updatePassword(eq(15L), eq("$2a$12$newHash"), any(Instant.class));
        verify(passwordHistoryMapper).insert(eq(15L), eq("$2a$12$newHash"), any(Instant.class));
    }

    @Test
    @DisplayName("REQ-AUTH-009: 변경 성공 시 해당 사용자의 모든 Refresh Token 무효화")
    void changePassword_revokesAllRefreshTokens() {
        User user = activeUser(16L, "admin", 0);
        when(userMapper.findById(16L)).thenReturn(Optional.of(user));
        when(passwordPolicyService.matches("OldP@ss123", user.getPasswordHash())).thenReturn(true);
        when(passwordHistoryMapper.findRecentHashes(16L, 5)).thenReturn(List.of());
        when(passwordPolicyService.matches(eq("NewP@ss456!"), any())).thenReturn(false);
        when(passwordPolicyService.hash("NewP@ss456!")).thenReturn("$2a$12$newHash");

        authService.changePassword(16L, "OldP@ss123", "NewP@ss456!");

        verify(refreshTokenMapper).revokeAllForUser(eq(16L), any(Instant.class));
    }

    @Test
    @DisplayName("REQ-AUTH-009: @AuditLog 어노테이션 — changePassword 메서드에 PASSWORD_CHANGE 적용 확인")
    void changePassword_recordsAuditLog() throws NoSuchMethodException {
        // @AuditLog 어노테이션이 changePassword 구현체에 선언되어 있는지 간접 검증
        var method = AuthServiceImpl.class.getDeclaredMethod(
                "changePassword", long.class, String.class, String.class);
        var annotation = method.getAnnotation(kr.co.ircp.cms.domain.audit.annotation.AuditLog.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.action()).isEqualTo("PASSWORD_CHANGE");
        assertThat(annotation.entityType()).isEqualTo("User");
    }

    // ============================================================
    // REQ-AUTH-017: 비밀번호 재설정
    // ============================================================

    @Test
    @DisplayName("REQ-AUTH-017-D-3: requestPasswordReset — 등록된 이메일이면 VerificationService.request 호출")
    void requestPasswordReset_invokesVerification() {
        // given — SPEC-CMS-SECURITY-PII-001 V24 적용 후 HMAC lookup 으로 변경
        String email = "user@example.com";
        String emailHmac = "hmac-of-" + email.toLowerCase();
        User user = activeUser(100L, "user", 0);
        when(userMapper.findByEmailHmac(emailHmac)).thenReturn(Optional.of(user));

        // when
        authService.requestPasswordReset(email, "127.0.0.1", "JUnit");

        // then
        verify(verificationService).request(any(), eq("127.0.0.1"), eq("JUnit"));
    }

    @Test
    @DisplayName("REQ-AUTH-017-D-3: requestPasswordReset — 미등록 이메일이면 VerificationService 미호출 (enumeration 방지)")
    void requestPasswordReset_returnsSameMessage_forNonExistentEmail() {
        // given
        String email = "unknown@example.com";
        String emailHmac = "hmac-of-" + email.toLowerCase();
        when(userMapper.findByEmailHmac(emailHmac)).thenReturn(Optional.empty());

        // when — 예외 없이 정상 반환
        authService.requestPasswordReset(email, "127.0.0.1", "JUnit");

        // then — verificationService는 호출되지 않음
        verify(verificationService, never()).request(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("REQ-AUTH-017-D-4: confirmPasswordReset — 성공 시 비밀번호 변경 + 세션 무효화")
    void confirmPasswordReset_appliesNewPassword_andRevokesRefresh() {
        // given
        String token = "a".repeat(64);
        String email = "user@example.com";
        String emailHmac = "hmac-of-" + email.toLowerCase();
        VerificationRequest vr = VerificationRequest.builder()
            .status(VerificationStatus.VERIFIED)
            .verifiedAt(Instant.now().minusSeconds(10))
            .purpose(VerificationPurpose.PASSWORD_RESET)
            .target(email)
            .build();
        when(verificationService.validateVerifiedToken(token, VerificationPurpose.PASSWORD_RESET))
            .thenReturn(Optional.of(vr));
        User user = activeUser(101L, "user", 0);
        when(userMapper.findByEmailHmac(emailHmac)).thenReturn(Optional.of(user));
        when(passwordHistoryMapper.findRecentHashes(101L, 5)).thenReturn(List.of());
        when(passwordPolicyService.matches(eq("NewP@ss789!"), any())).thenReturn(false);
        when(passwordPolicyService.hash("NewP@ss789!")).thenReturn("$2a$12$newHash");

        // when
        authService.confirmPasswordReset(token, "NewP@ss789!");

        // then
        verify(userMapper).updatePassword(eq(101L), eq("$2a$12$newHash"), any(Instant.class));
        verify(refreshTokenMapper).revokeAllForUser(eq(101L), any(Instant.class));
        verify(emailService).sendPasswordResetNotice(email);
    }

    @Test
    @DisplayName("REQ-AUTH-017-D-4: confirmPasswordReset — 만료된 verifiedToken → InvalidVerifiedTokenException")
    void confirmPasswordReset_throwsInvalidToken_onExpired() {
        // given
        String token = "b".repeat(64);
        when(verificationService.validateVerifiedToken(token, VerificationPurpose.PASSWORD_RESET))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.confirmPasswordReset(token, "AnyP@ss123"))
            .isInstanceOf(InvalidVerifiedTokenException.class);
    }

    // ============================================================
    // 헬퍼
    // ============================================================

    /**
     * ACTIVE 상태 User 빌더 헬퍼.
     */
    private User activeUser(long id, String username, int failCount) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$12$hash")
                .status(UserStatus.ACTIVE)
                .failCount(failCount)
                .build();
    }

    /**
     * 테스트용 SHA-256 Hex — AuthServiceImpl의 내부 로직과 동일하게 유지.
     */
    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
