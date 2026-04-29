package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PasswordPolicyService RED 단계 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004 — 8자 이상, 3종류 이상 문자, BCrypt strength=12.
 * 모든 테스트는 UnsupportedOperationException으로 실패해야 한다 (RED 의도).
 */
// @MX:TODO: [AUTO] Step 2 GREEN — BCrypt 정책 검증 로직 구현 후 UOE를 실제 검증으로 교체
@DisplayName("PasswordPolicyService RED 단계 테스트")
class PasswordPolicyServiceTest {

    private PasswordPolicyService passwordPolicyService;

    @BeforeEach
    void setUp() {
        passwordPolicyService = new PasswordPolicyServiceImpl();
    }

    @Test
    @DisplayName("validate — 정책 준수 비밀번호 통과 (RED: UOE)")
    void validate_passes_for_compliant() {
        // 8자 이상, 대문자+소문자+숫자+특수문자 포함 — 정책 준수
        assertThatThrownBy(() ->
                passwordPolicyService.validate("ValidP@ss123")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("validate — 8자 미만 시 정책 위반 예외 (RED: UOE)")
    void validate_throws_when_too_short() {
        assertThatThrownBy(() ->
                passwordPolicyService.validate("Ab1!")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("validate — 3종류 미만 문자 시 정책 위반 예외 (RED: UOE)")
    void validate_throws_when_lacks_3_types() {
        // 소문자만 사용 — 1종류
        assertThatThrownBy(() ->
                passwordPolicyService.validate("password")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("hash — BCrypt strength=12로 해싱 (RED: UOE)")
    void hash_returnsBcryptStrength12() {
        assertThatThrownBy(() ->
                passwordPolicyService.hash("ValidP@ss123")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("matches — 올바른 비밀번호와 해시 일치 (RED: UOE)")
    void matches_returnsTrue_for_correctPassword() {
        assertThatThrownBy(() ->
                passwordPolicyService.matches("ValidP@ss123", "$2a$12$hash")
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("matches — 잘못된 비밀번호와 해시 불일치 (RED: UOE)")
    void matches_returnsFalse_for_wrong() {
        assertThatThrownBy(() ->
                passwordPolicyService.matches("wrongPassword", "$2a$12$hash")
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
