package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.config.JwtProperties;
import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.dto.PublicRegisterRequest;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.exception.UserPendingApprovalException;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.PasswordHistoryMapper;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import kr.co.ircp.cms.domain.system.setting.dto.SystemSettingResponse;
import kr.co.ircp.cms.domain.system.setting.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC-CMS-USER-APPROVAL-001 — 가입 승인 게이트(REQ-UA-001/002/003) 및
 * PENDING_APPROVAL 로그인 차단(REQ-UA-004) 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 가입 승인 게이트 테스트 (SPEC-CMS-USER-APPROVAL-001)")
class AuthServiceImplApprovalTest {

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
    @Mock private EmailEncryptionService emailEncryptionService;
    @Mock private SystemSettingService systemSettingService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "test-secret-256-bits-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                Duration.ofMinutes(15), Duration.ofDays(7), "iroum-cms-test");
        authService = new AuthServiceImpl(
                userMapper, refreshTokenMapper, loginHistoryMapper,
                tokenBlacklistMapper, jwtTokenProvider, passwordPolicyService,
                passwordHistoryMapper, jwtProperties, permissionService,
                verificationService, emailService, emailEncryptionService,
                systemSettingService);
    }

    private void stubRegistrationCommon() {
        when(userMapper.existsByUsername(anyString())).thenReturn(false);
        when(emailEncryptionService.computeHmac(anyString())).thenReturn("hmac-x");
        when(userMapper.existsByEmailHmac(anyString())).thenReturn(false);
        // EncryptedEmail 은 IV 12바이트·tag 16바이트 검증을 생성자에서 수행하므로 유효 길이 사용.
        when(emailEncryptionService.encrypt(anyString()))
                .thenReturn(new EncryptedEmail(new byte[16], new byte[12], new byte[16], 1));
        lenient().when(passwordPolicyService.hash(anyString())).thenReturn("$2a$hashed");
        // useGeneratedKeys 모사 — insert 시 생성 PK 를 주입한다.
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, User.class).setId(100L);
            return null;
        }).when(userMapper).insert(any(User.class));
    }

    private static SystemSettingResponse boolSetting(String value) {
        return new SystemSettingResponse(
                "REGISTRATION_APPROVAL_REQUIRED", value, "BOOL", "게이트", null);
    }

    private static PublicRegisterRequest req() {
        return new PublicRegisterRequest("citizen@example.com", "Passw0rd!", "시민", null);
    }

    @Test
    @DisplayName("REQ-UA-001 — 게이트 ON: PENDING_APPROVAL 생성 + JWT 미발급 + 역할 미부여")
    void register_gateOn_returnsPendingApproval_noJwt() {
        stubRegistrationCommon();
        when(systemSettingService.get("REGISTRATION_APPROVAL_REQUIRED"))
                .thenReturn(boolSetting("true"));

        AuthService.RegisterResult result =
                authService.registerPublicUser(req(), "127.0.0.1", "JUnit");

        assertThat(result).isInstanceOf(AuthService.RegisterResult.PendingApproval.class);
        // 보류 상태로 INSERT 되었는지 확인
        verify(userMapper).insert(argThat(u -> u.getStatus() == UserStatus.PENDING_APPROVAL));
        // JWT/역할/refresh 미발급
        verify(userMapper, never()).insertRole(anyLong(), anyString(), any(), any());
        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString(), any(), any());
        verify(refreshTokenMapper, never()).insert(any());
    }

    @Test
    @DisplayName("REQ-UA-002 — 게이트 OFF: ACTIVE 생성 + MEMBER 역할 + JWT 발급(Approved)")
    void register_gateOff_returnsApproved_withJwt() {
        stubRegistrationCommon();
        when(systemSettingService.get("REGISTRATION_APPROVAL_REQUIRED"))
                .thenReturn(boolSetting("false"));
        when(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), any(), any()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh-token");
        when(userMapper.findRoleCodesByUserId(anyLong())).thenReturn(Set.of("MEMBER"));
        when(permissionService.findEffectivePermissionsForUser(anyLong())).thenReturn(Set.of());

        AuthService.RegisterResult result =
                authService.registerPublicUser(req(), "127.0.0.1", "JUnit");

        assertThat(result).isInstanceOf(AuthService.RegisterResult.Approved.class);
        AuthService.RegisterResult.Approved approved = (AuthService.RegisterResult.Approved) result;
        assertThat(approved.loginOutcome().response().accessToken()).isEqualTo("access-token");
        verify(userMapper).insert(argThat(u -> u.getStatus() == UserStatus.ACTIVE));
        verify(userMapper).insertRole(anyLong(), eq("MEMBER"), any(), any());
    }

    @Test
    @DisplayName("REQ-UA-003 — 설정 키 미존재 시 게이트 OFF 로 간주(회귀 방지)")
    void register_settingMissing_defaultsToGateOff() {
        stubRegistrationCommon();
        when(systemSettingService.get("REGISTRATION_APPROVAL_REQUIRED"))
                .thenThrow(new NoSuchElementException("not found"));
        when(jwtTokenProvider.generateAccessToken(anyLong(), anyString(), any(), any()))
                .thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(anyLong())).thenReturn("refresh-token");
        when(userMapper.findRoleCodesByUserId(anyLong())).thenReturn(Set.of("MEMBER"));
        when(permissionService.findEffectivePermissionsForUser(anyLong())).thenReturn(Set.of());

        AuthService.RegisterResult result =
                authService.registerPublicUser(req(), "127.0.0.1", "JUnit");

        assertThat(result).isInstanceOf(AuthService.RegisterResult.Approved.class);
        verify(userMapper).insert(argThat(u -> u.getStatus() == UserStatus.ACTIVE));
    }

    @Test
    @DisplayName("REQ-UA-004 — PENDING_APPROVAL 사용자 로그인은 거부(UserPendingApprovalException)")
    void login_pendingApproval_isRejected() {
        User pending = User.builder()
                .id(10L).username("citizen@example.com")
                .passwordHash("$2a$hashed").name("시민")
                .status(UserStatus.PENDING_APPROVAL)
                .build();
        when(userMapper.findByUsername("citizen@example.com")).thenReturn(java.util.Optional.of(pending));

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("citizen@example.com", "Passw0rd!"), "127.0.0.1", "JUnit"))
                .isInstanceOf(UserPendingApprovalException.class);

        // 비밀번호 검증까지 가지 않고 차단
        verify(passwordPolicyService, never()).matches(anyString(), anyString());
        verify(loginHistoryMapper).insert(argThat(h -> "PENDING_APPROVAL".equals(h.getFailureReason())));
    }
}
