package kr.co.ircp.cms.common.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PiiMaskingConverter 단위 테스트 — SPEC-CMS-SECURITY-PII-MASKING-001 REQ-PII-MASK-001.
 *
 * <p>AC 매핑:
 * <ul>
 *   <li>AC-MASK-001-1: email 마스킹</li>
 *   <li>AC-MASK-001-2: 휴대전화 마스킹</li>
 *   <li>AC-MASK-001-3: 주민등록번호 마스킹</li>
 *   <li>AC-MASK-001-4: IPv4 마스킹</li>
 *   <li>AC-MASK-001-5: false positive 미발생(일반 문자열은 변경 없음)</li>
 * </ul>
 */
@DisplayName("PiiMaskingConverter — Logback PII 정규식 마스킹")
class LogbackPiiMaskingTest {

    @Nested
    @DisplayName("AC-MASK-001-1: email 패턴")
    class EmailMasking {

        @Test
        @DisplayName("일반 email은 첫 글자 + ***@***.*** 로 변환된다")
        void mask_basic_email() {
            String result = PiiMaskingConverter.mask("user signed in: john@example.com");
            assertThat(result).isEqualTo("user signed in: j***@***.***");
        }

        @Test
        @DisplayName("dot/+/숫자가 포함된 email도 마스킹된다")
        void mask_complex_email() {
            String result = PiiMaskingConverter.mask("contact: alice.smith+tag123@sub.example.co.kr");
            assertThat(result).isEqualTo("contact: a***@***.***");
        }

        @Test
        @DisplayName("문장 내 다중 email은 각각 마스킹된다")
        void mask_multiple_emails() {
            String result = PiiMaskingConverter.mask(
                    "from: bob@a.com to: carol@b.com");
            assertThat(result).isEqualTo("from: b***@***.*** to: c***@***.***");
        }
    }

    @Nested
    @DisplayName("AC-MASK-001-2: 휴대전화 패턴(국내)")
    class PhoneMasking {

        @Test
        @DisplayName("하이픈 포함 010-XXXX-XXXX는 가운데 4자리 마스킹된다")
        void mask_hyphenated_phone() {
            String result = PiiMaskingConverter.mask("call 010-1234-5678");
            assertThat(result).isEqualTo("call 010-****-5678");
        }

        @Test
        @DisplayName("하이픈 없는 01012345678도 마스킹된다")
        void mask_unhyphenated_phone() {
            String result = PiiMaskingConverter.mask("phone:01012345678");
            assertThat(result).isEqualTo("phone:010-****-5678");
        }

        @Test
        @DisplayName("011 등 다른 통신사 번호도 마스킹된다")
        void mask_other_carrier_phone() {
            String result = PiiMaskingConverter.mask("legacy 011-123-4567");
            assertThat(result).isEqualTo("legacy 011-****-4567");
        }
    }

    @Nested
    @DisplayName("AC-MASK-001-3: 주민등록번호(SSN) 패턴")
    class SsnMasking {

        @Test
        @DisplayName("주민등록번호 6+7은 뒷자리 7자가 마스킹된다")
        void mask_ssn() {
            String result = PiiMaskingConverter.mask("registration: 990101-1234567");
            assertThat(result).isEqualTo("registration: 990101-*******");
        }
    }

    @Nested
    @DisplayName("AC-MASK-001-4: IPv4 패턴")
    class Ipv4Masking {

        @Test
        @DisplayName("IPv4 4 옥텟은 뒷자리 2 옥텟이 마스킹된다")
        void mask_ipv4() {
            String result = PiiMaskingConverter.mask("client_ip=192.168.1.100 connected");
            assertThat(result).isEqualTo("client_ip=192.168.***.*** connected");
        }

        @Test
        @DisplayName("IPv4 다중 등장도 각각 마스킹된다")
        void mask_multiple_ipv4() {
            String result = PiiMaskingConverter.mask("from 10.0.0.5 to 172.16.0.1");
            assertThat(result).isEqualTo("from 10.0.***.*** to 172.16.***.***");
        }
    }

    @Nested
    @DisplayName("AC-MASK-001-5: false positive 미발생")
    class FalsePositive {

        @Test
        @DisplayName("일반 영문 문장은 변경되지 않는다")
        void no_match_normal_text() {
            String input = "hello world this is a log message without PII";
            String result = PiiMaskingConverter.mask(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("숫자 단독 문자열은 IPv4로 오인되지 않는다")
        void no_match_plain_number() {
            String input = "count=12345 result=ok";
            String result = PiiMaskingConverter.mask(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("null 입력은 빈 문자열을 반환한다")
        void null_returns_empty() {
            assertThat(PiiMaskingConverter.mask(null)).isEqualTo("");
        }

        @Test
        @DisplayName("빈 문자열은 그대로 반환된다")
        void empty_returns_empty() {
            assertThat(PiiMaskingConverter.mask("")).isEqualTo("");
        }
    }
}
