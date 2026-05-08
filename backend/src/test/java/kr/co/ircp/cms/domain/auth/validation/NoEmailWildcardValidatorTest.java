package kr.co.ircp.cms.domain.auth.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NoEmailWildcardValidator 단위 테스트.
 *
 * <p>REQ-PII-EMAIL-007 — email 파라미터의 와일드카드/부분일치 패턴 거부 검증.
 */
class NoEmailWildcardValidatorTest {

    private NoEmailWildcardValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NoEmailWildcardValidator();
        validator.initialize(null);
    }

    // ─── null / 빈 문자열 통과 ─────────────────────────────────────────────────

    @Test
    @DisplayName("null 입력은 통과한다 (파라미터 미포함 처리)")
    void null_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("빈 문자열은 통과한다 (전체 검색 분기)")
    void empty_isValid() {
        assertThat(validator.isValid("", null)).isTrue();
    }

    // ─── 정상 email 통과 ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "정상 email 통과: {0}")
    @ValueSource(strings = {
            "john.doe@example.com",
            "user+tag@example.co.kr",
            "alice@subdomain.example.com",
            "test-user@my-domain.org",
            "info@xn--mgbh0fb.xn--kgbechtv"   // IDN punycode
    })
    @DisplayName("RFC 5321 valid email은 통과한다")
    void validEmail_isValid(String email) {
        assertThat(validator.isValid(email, null)).isTrue();
    }

    // ─── 와일드카드 4종 거부 ──────────────────────────────────────────────────

    @ParameterizedTest(name = "와일드카드 패턴 거부: {0}")
    @ValueSource(strings = {
            "john*",          // * 와일드카드
            "*example.com",   // * 와일드카드
            "%doe%",          // % 와일드카드 (SQL)
            "john_"           // _ 와일드카드 (SQL)
    })
    @DisplayName("와일드카드 문자(*,%,_)가 포함된 입력은 거부한다")
    void wildcardPatterns_areRejected(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }

    // ─── @ 미포함 거부 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("@ 미포함 문자열은 거부한다")
    void noAtSign_isRejected() {
        assertThat(validator.isValid("test", null)).isFalse();
    }

    // ─── @-trailing partial 거부 ─────────────────────────────────────────────

    @Test
    @DisplayName("@로 끝나는 문자열(domain-part 없음)은 거부한다")
    void atTrailing_isRejected() {
        assertThat(validator.isValid("test@", null)).isFalse();
    }

    // ─── 기타 거부 케이스 ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "RFC 5321 위배 입력 거부: {0}")
    @ValueSource(strings = {
            "test@example",    // TLD 없음
            "@example.com",    // local-part 없음
    })
    @DisplayName("RFC 5321 형식 위배 입력은 거부한다")
    void invalidFormat_isRejected(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }

    @ParameterizedTest(name = "기타 메타문자 거부: {0}")
    @ValueSource(strings = {
            "test?@example.com",   // ? 메타문자
            "te[st@example.com",   // [ 메타문자
    })
    @DisplayName("정규식 메타문자가 포함된 입력은 거부한다")
    void regexMetaChars_areRejected(String email) {
        assertThat(validator.isValid(email, null)).isFalse();
    }
}
