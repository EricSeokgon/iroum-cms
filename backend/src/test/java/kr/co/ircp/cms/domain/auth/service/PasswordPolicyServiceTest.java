package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PasswordPolicyService 행동 검증 테스트.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-004 — 8자 이상, 3종류 이상 문자, BCrypt strength=12.
 * 구현이 올바르면 모두 GREEN.
 */
@DisplayName("PasswordPolicyService 행동 검증 테스트")
class PasswordPolicyServiceTest {

    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        service = new PasswordPolicyServiceImpl();
    }

    @Test
    @DisplayName("REQ-AUTH-004: 정상 비밀번호는 검증을 통과한다 (8자, 3종 조합)")
    void validate_passes_for_compliant() {
        // given
        String compliant = "ValidP@ss123";
        // when / then — 예외가 발생하지 않아야 한다
        service.validate(compliant);
    }

    @Test
    @DisplayName("REQ-AUTH-004: 8자 미만 비밀번호는 거부된다")
    void validate_throws_when_too_short() {
        assertThatThrownBy(() -> service.validate("Ab1!"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("8자");
    }

    @Test
    @DisplayName("REQ-AUTH-004: 3종 미만 조합은 거부된다 (대문자만 8자)")
    void validate_throws_when_lacks_3_types() {
        assertThatThrownBy(() -> service.validate("ABCDEFGH"))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("3종");
    }

    @Test
    @DisplayName("REQ-AUTH-004: BCrypt strength 12 해시 형식 ($2a$12$ 또는 $2b$12$)")
    void hash_returnsBcryptStrength12() {
        // when
        String hash = service.hash("ValidP@ss123");
        // then — BCrypt 출력 형식: $2a$12$... 또는 $2b$12$...
        assertThat(hash).matches("^\\$2[aby]\\$12\\$.{53}$");
    }

    @Test
    @DisplayName("REQ-AUTH-004: 동일한 raw 비밀번호 매칭 성공")
    void matches_returnsTrue_for_correctPassword() {
        String raw = "ValidP@ss123";
        String hash = service.hash(raw);
        assertThat(service.matches(raw, hash)).isTrue();
    }

    @Test
    @DisplayName("REQ-AUTH-004: 다른 raw 비밀번호 매칭 실패")
    void matches_returnsFalse_for_wrong() {
        String hash = service.hash("ValidP@ss123");
        assertThat(service.matches("WrongP@ss456", hash)).isFalse();
    }
}
