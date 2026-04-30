package kr.co.ircp.cms.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveFieldMasker 단위 테스트 — REQ-CROSS-001-D-4.
 */
@DisplayName("SensitiveFieldMasker 단위 테스트")
class SensitiveFieldMaskerTest {

    private SensitiveFieldMasker masker;

    @BeforeEach
    void setUp() {
        masker = new SensitiveFieldMasker(new ObjectMapper());
    }

    @Test
    @DisplayName("shouldMaskPasswordFieldInJson — password 필드는 '***'로 마스킹된다")
    void shouldMaskPasswordFieldInJson() throws Exception {
        // given
        String json = """
                {"username":"admin","password":"secret123","email":"test@example.com"}
                """;

        // when
        String masked = masker.maskJson(json);

        // then
        assertThat(masked)
                .contains("\"password\":\"***\"")
                .contains("\"email\":\"***\"")     // email도 마스킹 대상
                .contains("\"username\":\"admin\"") // username은 마스킹 대상 아님
                .doesNotContain("secret123");
    }

    @Test
    @DisplayName("shouldMaskMultipleSensitiveFields — 여러 민감 필드를 동시에 마스킹한다")
    void shouldMaskMultipleSensitiveFields() throws Exception {
        // given
        String json = """
                {
                  "name": "홍길동",
                  "phone": "010-1234-5678",
                  "ssn": "900101-1234567",
                  "creditCardNumber": "4111-1111-1111-1111",
                  "address": "서울시 강남구"
                }
                """;

        // when
        String masked = masker.maskJson(json);

        // then
        assertThat(masked)
                .contains("\"phone\":\"***\"")
                .contains("\"ssn\":\"***\"")
                .contains("\"creditCardNumber\":\"***\"")
                .contains("\"name\":\"홍길동\"")    // 마스킹 대상 아님
                .contains("\"address\":\"서울시 강남구\"") // 마스킹 대상 아님
                .doesNotContain("010-1234-5678")
                .doesNotContain("900101-1234567");
    }

    @Test
    @DisplayName("shouldNotMaskNonSensitiveFields — 민감 키워드가 없는 필드는 마스킹되지 않는다")
    void shouldNotMaskNonSensitiveFields() throws Exception {
        // given
        String json = """
                {"title":"공지사항","content":"안녕하세요","status":"ACTIVE"}
                """;

        // when
        String masked = masker.maskJson(json);

        // then
        assertThat(masked)
                .contains("\"title\":\"공지사항\"")
                .contains("\"content\":\"안녕하세요\"")
                .contains("\"status\":\"ACTIVE\"")
                .doesNotContain("***");
    }
}
