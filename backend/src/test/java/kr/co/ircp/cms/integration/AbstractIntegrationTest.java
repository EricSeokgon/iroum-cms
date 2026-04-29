package kr.co.ircp.cms.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 통합 테스트 베이스 클래스.
 *
 * <p>PostgreSQL 16-alpine 컨테이너를 공유하고 Flyway V1~V9를 실행한다.
 * @ServiceConnection으로 DataSource를 자동 주입 (Spring Boot 3.1+).
 *
 * <p>SPEC-CMS-002 Bundle A — 핵심 인증 흐름 통합 검증.
 */
// @MX:ANCHOR: [AUTO] AbstractIntegrationTest — 모든 IT 클래스의 공통 베이스
// @MX:REASON: UserMapperIT, OrgMapperIT, AuditTriggerIT 등 5+ 클래스가 상속 (fan_in >= 3)
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {

    // @MX:NOTE: [AUTO] postgres:16-alpine — tech.md §1 PostgreSQL 16 버전과 일치
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iroum_cms_test")
                    .withUsername("test")
                    .withPassword("test");
}
