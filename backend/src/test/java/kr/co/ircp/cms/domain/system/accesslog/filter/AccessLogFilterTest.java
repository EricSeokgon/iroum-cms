package kr.co.ircp.cms.domain.system.accesslog.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccessLogFilter 유닛 테스트.
 * REQ-SYSTEM-001-D: SHA-256 IP 익명화 검증
 *
 * <p>Filter 자체 IO 동작 테스트는 통합 테스트로 분리.
 * 이 클래스는 순수 로직(hashIp)만 검증한다.
 */
@DisplayName("AccessLogFilter 유닛 테스트 (REQ-SYSTEM-001-D)")
class AccessLogFilterTest {

    @Test
    @DisplayName("hashIp() — 같은 IP/salt는 항상 같은 해시값")
    void hashIp_same_input_same_output() {
        // given
        String ip = "192.168.1.100";
        String salt = "test-salt";

        // when
        String hash1 = AccessLogFilter.hashIp(ip, salt);
        String hash2 = AccessLogFilter.hashIp(ip, salt);

        // then
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hashIp() — 다른 IP는 다른 해시값")
    void hashIp_different_ip_different_hash() {
        // given
        String salt = "test-salt";

        // when
        String hash1 = AccessLogFilter.hashIp("10.0.0.1", salt);
        String hash2 = AccessLogFilter.hashIp("10.0.0.2", salt);

        // then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("hashIp() — SHA-256 결과는 64자리 hex")
    void hashIp_produces_64_char_hex() {
        // when
        String hash = AccessLogFilter.hashIp("127.0.0.1", "any-salt");

        // then
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }
}
