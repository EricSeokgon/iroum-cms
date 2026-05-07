package kr.co.ircp.cms.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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
 *
 * <p>실행 방법:
 * <pre>
 *   # 단위 테스트만 (Docker 불필요)
 *   ./gradlew test
 *
 *   # 통합 테스트 포함 (Docker 필요)
 *   ./gradlew integrationTest
 * </pre>
 */
// @MX:ANCHOR: [AUTO] AbstractIntegrationTest — 모든 IT 클래스의 공통 베이스
// @MX:REASON: UserMapperIT, OrgMapperIT, AuditTriggerIT 등 5+ 클래스가 상속 (fan_in >= 3)
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {

    // @MX:NOTE: [AUTO] Singleton Container — @Testcontainers/@Container 대신 static init 사용
    // 클래스 로딩 시 한 번만 시작, JVM 종료 시 Ryuk이 정리
    // Docker 미설치 환경: POSTGRES = null → @BeforeAll에서 assumeTrue(false) → SKIP 처리
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        PostgreSQLContainer<?> container = null;
        try {
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iroum_cms_test")
                    .withUsername("test")
                    .withPassword("test");
            container.start();
        } catch (Exception e) {
            // Docker 미설치 환경 — @BeforeAll assumeTrue가 SKIP 처리
        }
        POSTGRES = container;
    }

    @BeforeAll
    static void assumeDockerAvailable() {
        Assumptions.assumeTrue(
                POSTGRES != null && POSTGRES.isRunning(),
                "Docker 미설치 환경 — 통합 테스트 건너뜀 (./gradlew integrationTest으로 실행)"
        );
    }

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null && POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }
}
