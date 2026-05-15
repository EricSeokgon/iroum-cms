package kr.co.ircp.cms.domain.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link HashUtil} 단위 테스트 — SPEC-CMS-002 Step 3 REFACTOR.
 *
 * <p>커버리지 갭 P1: 75% → 100% (LINE)
 * <p>대상 ANCHOR: 토큰 해시 계산의 단일 진실점 (fan_in ≥ 3)
 */
@DisplayName("HashUtil — SHA-256 해시 유틸리티")
class HashUtilTest {

    @Test
    @DisplayName("빈 문자열의 SHA-256은 표준값과 일치")
    void shouldHashEmptyString() {
        // SHA-256("") 의 표준 결과값
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertThat(HashUtil.sha256Hex("")).isEqualTo(expected);
    }

    @Test
    @DisplayName("ASCII 입력 'abc'의 SHA-256은 표준값과 일치")
    void shouldHashAbc() {
        // SHA-256("abc") 의 표준 결과값
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertThat(HashUtil.sha256Hex("abc")).isEqualTo(expected);
    }

    @Test
    @DisplayName("결과는 항상 소문자 16진수 64자")
    void shouldReturn64CharHexLowercase() {
        String result = HashUtil.sha256Hex("any-arbitrary-input-value-123");

        assertThat(result).hasSize(64);
        assertThat(result).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("동일 입력은 항상 동일 결과 (결정성)")
    void shouldBeDeterministic() {
        String a = HashUtil.sha256Hex("same-input");
        String b = HashUtil.sha256Hex("same-input");

        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("서로 다른 입력은 서로 다른 결과 (충돌 회피)")
    void shouldProduceDifferentHashesForDifferentInputs() {
        String a = HashUtil.sha256Hex("input-1");
        String b = HashUtil.sha256Hex("input-2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("UTF-8 멀티바이트 입력 (한글) 해시 검증")
    void shouldHashUtf8MultiByteInput() {
        // 한글 문자열도 NPE/예외 없이 처리되어야 함
        String result = HashUtil.sha256Hex("안녕하세요");

        assertThat(result).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("HashUtil 인스턴스화는 private 생성자에 의해 차단됨")
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<HashUtil> ctor = HashUtil.class.getDeclaredConstructor();
        assertThat(ctor.canAccess(null)).isFalse();
        ctor.setAccessible(true);
        // 인스턴스 생성 자체는 reflection으로만 가능 — 정상 동작 확인
        HashUtil instance = ctor.newInstance();
        assertThat(instance).isNotNull();
    }
}
