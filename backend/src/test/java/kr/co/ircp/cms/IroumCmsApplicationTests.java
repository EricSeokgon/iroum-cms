package kr.co.ircp.cms;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 애플리케이션 컨텍스트 로드 통합 테스트.
 *
 * <p>Testcontainers로 PostgreSQL 16 컨테이너를 구동하여
 * 실제 DB 연결 및 Flyway 마이그레이션까지 검증한다.
 * 별도 PostgreSQL 서버 없이 Docker만 설치되어 있으면 실행 가능하다.
 */
@SpringBootTest
@Testcontainers
class IroumCmsApplicationTests {

    // @MX:NOTE: [AUTO] postgres:16-alpine — tech.md §1 PostgreSQL 16 버전과 동일하게 유지
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iroum_cms_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void overrideDataSourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        // 테스트 전용 더미 PII 키 (32 bytes base64)
        registry.add("pii.keyvault.keys.v1", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        registry.add("pii.keyvault.hmac-key", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
    }

    @Test
    void contextLoads() {
        // 컨텍스트가 오류 없이 로드되면 테스트 성공
    }
}
