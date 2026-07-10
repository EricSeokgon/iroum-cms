package kr.co.ircp.cms.integration.approval;

import kr.co.ircp.cms.domain.auth.dto.PublicRegisterRequest;
import kr.co.ircp.cms.domain.auth.entity.VerificationPurpose;
import kr.co.ircp.cms.domain.auth.exception.RegistrationTokenInvalidException;
import kr.co.ircp.cms.domain.auth.exception.RegistrationTokenRequiredException;
import kr.co.ircp.cms.domain.auth.service.AuthService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC-CMS-USER-APPROVAL-002 — 가입 이메일 인증 연동 통합 테스트 (실제 PostgreSQL).
 *
 * <p>REQ-UA2-002 — verifiedToken 검증 및 가입 접근 제어:
 * 인증 게이트 OFF 회귀(AC-002-4), 게이트 ON + 토큰 누락 400(AC-002-2),
 * 게이트 ON + 토큰 무효 403(AC-002-3), 게이트 ON + 토큰 유효 시 email_verified_at 기록(REQ-UA2-002).
 */
@DisplayName("가입 이메일 인증 연동 통합 테스트 (SPEC-CMS-USER-APPROVAL-002)")
class RegisterEmailVerifyIT extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String VERIFY_KEY = "REGISTRATION_EMAIL_VERIFY_REQUIRED";

    @AfterEach
    void resetVerifyGate() {
        jdbcTemplate.update(
                "UPDATE system_setting SET value = 'false' WHERE key = ?", VERIFY_KEY);
    }

    private void setVerifyRequired(boolean required) {
        jdbcTemplate.update(
                "UPDATE system_setting SET value = ? WHERE key = ?",
                String.valueOf(required), VERIFY_KEY);
    }

    /** confirm 을 거치지 않고 verification_request 행을 직접 시드해 verifiedToken 을 발급한다. */
    private String seedVerifiedToken(String email, VerificationPurpose purpose, Instant verifiedAt) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                "INSERT INTO verification_request " +
                "(request_id, channel, target, purpose, code_hash, created_at, expires_at, " +
                " attempts, max_attempts, status, verified_at, verified_token, requester_ip_hash, user_agent) " +
                "VALUES (?, 'EMAIL', ?, ?, 'x', ?, ?, 1, 5, 'VERIFIED', ?, ?, 'h', 'ua')",
                UUID.randomUUID(), email, purpose.name(),
                Timestamp.from(Instant.now()), Timestamp.from(verifiedAt.plusSeconds(300)),
                Timestamp.from(verifiedAt), token);
        return token;
    }

    @Test
    @DisplayName("AC-UA2-002-4 — 인증 게이트 OFF: verifiedToken 없이도 기존 가입 동작(회귀 없음)")
    void register_verifyOff_succeedsWithoutToken() {
        setVerifyRequired(false);
        String email = "verify_off_" + System.nanoTime() + "@example.com";

        AuthService.RegisterResult result = authService.registerPublicUser(
                new PublicRegisterRequest(email, "Password123!", "인증OFF", null),
                "127.0.0.1", "test-agent");

        // 승인 게이트 OFF(기본) → Approved
        assertThat(result).isInstanceOf(AuthService.RegisterResult.Approved.class);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-UA2-002-2 — 인증 게이트 ON + verifiedToken 누락: 400 + 사용자 미생성")
    void register_verifyOn_missingToken_throwsBadRequest() {
        setVerifyRequired(true);
        String email = "verify_missing_" + System.nanoTime() + "@example.com";

        assertThatThrownBy(() -> authService.registerPublicUser(
                new PublicRegisterRequest(email, "Password123!", "토큰누락", null),
                "127.0.0.1", "test-agent"))
                .isInstanceOf(RegistrationTokenRequiredException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, email);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("AC-UA2-002-3 — 인증 게이트 ON + 무효 verifiedToken: 403 + 사용자 미생성")
    void register_verifyOn_invalidToken_throwsForbidden() {
        setVerifyRequired(true);
        String email = "verify_invalid_" + System.nanoTime() + "@example.com";

        assertThatThrownBy(() -> authService.registerPublicUser(
                new PublicRegisterRequest(email, "Password123!", "토큰무효", "no-such-token"),
                "127.0.0.1", "test-agent"))
                .isInstanceOf(RegistrationTokenInvalidException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, email);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("AC-UA2-002-3 — 인증 게이트 ON + purpose 불일치 토큰: 403 + 사용자 미생성")
    void register_verifyOn_wrongPurpose_throwsForbidden() {
        setVerifyRequired(true);
        String email = "verify_purpose_" + System.nanoTime() + "@example.com";
        // PASSWORD_RESET 목적 토큰 → SIGNUP 검증에서 거부되어야 함
        String token = seedVerifiedToken(email, VerificationPurpose.PASSWORD_RESET, Instant.now());

        assertThatThrownBy(() -> authService.registerPublicUser(
                new PublicRegisterRequest(email, "Password123!", "목적불일치", token),
                "127.0.0.1", "test-agent"))
                .isInstanceOf(RegistrationTokenInvalidException.class);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Long.class, email);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("REQ-UA2-002 — 인증 게이트 ON + 유효 SIGNUP 토큰: 가입 성공 + email_verified_at 기록")
    void register_verifyOn_validToken_setsEmailVerifiedAt() {
        setVerifyRequired(true);
        String email = "verify_ok_" + System.nanoTime() + "@example.com";
        String token = seedVerifiedToken(email, VerificationPurpose.SIGNUP, Instant.now());

        authService.registerPublicUser(
                new PublicRegisterRequest(email, "Password123!", "인증완료", token),
                "127.0.0.1", "test-agent");

        Instant verifiedAt = jdbcTemplate.queryForObject(
                "SELECT email_verified_at FROM users WHERE username = ?", Instant.class, email);
        assertThat(verifiedAt).isNotNull();
    }
}
