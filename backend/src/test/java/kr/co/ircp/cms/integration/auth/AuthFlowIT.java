package kr.co.ircp.cms.integration.auth;

import kr.co.ircp.cms.domain.auth.dto.LoginRequest;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.entity.UserStatus;
import kr.co.ircp.cms.domain.auth.repository.RefreshTokenMapper;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인증 E2E 흐름 통합 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001, 002, 009 — 실제 PostgreSQL 컨테이너에서
 * 로그인 → 토큰 갱신 → 비밀번호 변경 흐름을 검증한다.
 *
 * <p>이메일 발송은 application-integration.yml에서 JavaMailSender를 Mock으로 대체
 * (spring.mail.host=localhost, 실제 SMTP 없음). 이메일 관련 플로우는 별도 단위 테스트에서 커버.
 */
// @MX:NOTE: [AUTO] AuthFlowIT — SPEC-CMS-002 Bundle A 핵심 E2E 검증
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private static final String RAW_PASSWORD = "ValidP@ss1!";

    @BeforeEach
    @Transactional
    void setUp() {
        testUser = User.builder()
                .username("auth_flow_it_user")
                .email("auth_flow_it@example.com")
                .passwordHash(passwordEncoder.encode(RAW_PASSWORD))
                .name("인증흐름테스트")
                .status(UserStatus.ACTIVE)
                .failCount(0)
                .passwordChangedAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userMapper.insert(testUser);
        userMapper.insertRole(testUser.getId(), "VIEWER", null, Instant.now());
    }

    @Test
    void loginFlow_persistsRefreshTokenAndLoginHistory() {
        // given
        LoginRequest req = new LoginRequest("auth_flow_it_user", RAW_PASSWORD);

        // when
        AuthService.LoginOutcome outcome = authService.login(req, "127.0.0.1", "TestAgent/1.0");

        // then — Access Token 발급
        assertThat(outcome.response().accessToken()).isNotBlank();
        assertThat(outcome.refreshToken()).isNotBlank();

        // then — refresh_tokens 행 존재
        Integer tokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL",
                Integer.class, testUser.getId());
        assertThat(tokenCount).isGreaterThanOrEqualTo(1);

        // then — login_history 기록
        Integer histCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM login_history WHERE user_id=? AND success=true",
                Integer.class, testUser.getId());
        assertThat(histCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void changePasswordFlow_revokesAllRefreshTokens() {
        // given — 먼저 로그인으로 refresh token 1개 생성
        LoginRequest req = new LoginRequest("auth_flow_it_user", RAW_PASSWORD);
        authService.login(req, "127.0.0.1", "TestAgent/1.0");

        // when — 비밀번호 변경
        String newPassword = "NewValidP@ss2!";
        assertThatNoException().isThrownBy(() ->
                authService.changePassword(testUser.getId(), RAW_PASSWORD, newPassword));

        // then — 기존 refresh_tokens 전부 revoke
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id=? AND revoked_at IS NULL",
                Integer.class, testUser.getId());
        assertThat(activeTokens).isZero();
    }

    @Test
    void loginWithWrongPassword_incrementsFailCount() {
        // given
        LoginRequest req = new LoginRequest("auth_flow_it_user", "wrong_password");

        // when / then
        assertThatThrownBy(() -> authService.login(req, "127.0.0.1", "TestAgent/1.0"))
                .isInstanceOf(kr.co.ircp.cms.domain.auth.exception.InvalidCredentialsException.class);

        // then — fail_count 증가
        User updated = userMapper.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getFailCount()).isGreaterThan(0);
    }
}
