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
            // SPEC-CMS-AI-003: pgvector/pgvector:pg16 = 공식 Postgres 16 + pgvector 확장(엄격한 상위 호환).
            // V33 RAG 마이그레이션의 CREATE EXTENSION vector 가 stock postgres:16-alpine 에는
            // 없으므로 pgvector 번들 이미지를 사용한다. 기존 IT(pgcrypto/pg_trgm 기반)는 무영향.
            container = new PostgreSQLContainer<>(
                    org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:pg16")
                            .asCompatibleSubstituteFor("postgres"))
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
        // SPEC-CMS-SECURITY-PII-001 — V24 적용 후 EmailEncryptionService 가 통합 컨텍스트에서
        // 기본 의존성으로 와이어링되므로, PII 키를 더미 32-byte base64 키로 주입한다.
        registry.add("pii.keyvault.keys.v1", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        registry.add("pii.keyvault.hmac-key", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
    }
}
