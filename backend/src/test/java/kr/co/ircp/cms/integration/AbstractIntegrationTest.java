package kr.co.ircp.cms.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 베이스 클래스.
 *
 * <p>PostgreSQL 16-alpine 컨테이너를 JVM 수명 동안 한 번만 시작하는 Singleton Container Pattern 사용.
 * @Testcontainers/@Container 조합은 테스트 클래스 간 컨테이너가 재시작되어 HikariCP 풀이 고갈되는
 * 문제가 있어 static 초기화 블록으로 대체. Ryuk이 JVM 종료 시 컨테이너를 정리한다.
 *
 * <p>SPEC-CMS-002 Bundle A — 핵심 인증 흐름 통합 검증.
 */
// @MX:ANCHOR: [AUTO] AbstractIntegrationTest — 모든 IT 클래스의 공통 베이스
// @MX:REASON: UserMapperIT, OrgMapperIT, AuditTriggerIT 등 5+ 클래스가 상속 (fan_in >= 3)
@SpringBootTest
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {

    // @MX:NOTE: [AUTO] Singleton Container — @Testcontainers/@Container 대신 static init 사용
    // 클래스 로딩 시 한 번만 시작, JVM 종료 시 Ryuk이 정리
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("iroum_cms_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
