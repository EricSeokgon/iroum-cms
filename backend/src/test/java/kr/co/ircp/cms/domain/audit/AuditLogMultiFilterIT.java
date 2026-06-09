package kr.co.ircp.cms.domain.audit;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 감사 로그 다중 값 action/severity 필터 통합 테스트.
 *
 * <p>SPEC-CMS-AUDIT-LOG-MULTI-FILTER-001 수락 기준 검증:
 * <ul>
 *   <li>AC-ALF-001: action 다중 값 IN 필터</li>
 *   <li>AC-ALF-002: severity 다중 값 IN 필터</li>
 *   <li>AC-ALF-003: action + severity 복합 필터</li>
 *   <li>AC-ALF-004: 필터 없음 → 전체 반환 (회귀)</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] AuditLogMultiFilterIT — action/severity 다중 IN 필터 IT
// @MX:SPEC: SPEC-CMS-AUDIT-LOG-MULTI-FILTER-001
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("감사 로그 다중 값 필터 IT (SPEC-CMS-AUDIT-LOG-MULTI-FILTER-001)")
class AuditLogMultiFilterIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iroum_cms_test")
                    .withUsername("test_user")
                    .withPassword("test_pass");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("pii.keyvault.keys.v1", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        registry.add("pii.keyvault.hmac-key", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String SEARCH_PATH = "/api/v1/system/audit-logs";

    /** ADMIN 토큰 stub 설정 */
    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "admin", Set.of("ADMIN"), Set.of(), Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    /**
     * 격리 식별자가 포함된 로그 행을 삽입한다.
     * entity_id 에 테스트 고유 prefix를 넣어 다른 IT 행과 구분한다.
     */
    private void insertLog(String entityIdPrefix, String action, String severity) {
        jdbc.update("""
                INSERT INTO audit_log (
                    event_time, actor_id, actor_role, action,
                    entity_type, entity_id,
                    ip_address, trace_id,
                    severity, result, duration_ms
                ) VALUES (
                    NOW(), NULL, 'TESTER', ?,
                    'MultiFilterIT', ?,
                    '127.0.0.1', 'trace-mf-it',
                    ?, 'SUCCESS', 5
                )
                """, action, entityIdPrefix + "-" + action + "-" + severity, severity);
    }

    @BeforeEach
    void seed() {
        // action: CREATE/UPDATE/DELETE × severity: INFO/WARN/CRITICAL = 9 rows
        for (String action : new String[]{"CREATE", "UPDATE", "DELETE"}) {
            for (String severity : new String[]{"INFO", "WARN", "CRITICAL"}) {
                insertLog("mf", action, severity);
            }
        }
    }

    @Nested
    @DisplayName("AC-ALF-001: action 다중 값 IN 필터")
    class ActionMultiFilter {

        @Test
        @DisplayName("action=CREATE&action=UPDATE → CREATE/UPDATE 행만 반환")
        void multiActionFilter_returnsBothActions() throws Exception {
            givenAdminToken();

            mockMvc.perform(get(SEARCH_PATH)
                            .param("action", "CREATE", "UPDATE")
                            .param("size", "100")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    // CREATE × 3 severity + UPDATE × 3 severity = 최소 6건 (다른 IT 행 있을 수 있으므로 >= 6)
                    .andExpect(jsonPath("$.items[?(@.action == 'DELETE')]").doesNotExist())
                    .andExpect(jsonPath("$.items[?(@.action == 'CREATE')]").exists())
                    .andExpect(jsonPath("$.items[?(@.action == 'UPDATE')]").exists());
        }

        @Test
        @DisplayName("action=DELETE 단일 값 → DELETE 행만 반환 (하위 호환)")
        void singleActionFilter_returnsOnlyDelete() throws Exception {
            givenAdminToken();

            mockMvc.perform(get(SEARCH_PATH)
                            .param("action", "DELETE")
                            .param("size", "100")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.action == 'CREATE')]").doesNotExist())
                    .andExpect(jsonPath("$.items[?(@.action == 'UPDATE')]").doesNotExist())
                    .andExpect(jsonPath("$.items[?(@.action == 'DELETE')]").exists());
        }
    }

    @Nested
    @DisplayName("AC-ALF-002: severity 다중 값 IN 필터")
    class SeverityMultiFilter {

        @Test
        @DisplayName("severity=WARN&severity=CRITICAL → WARN/CRITICAL 행만 반환")
        void multiSeverityFilter_returnsBothSeverities() throws Exception {
            givenAdminToken();

            mockMvc.perform(get(SEARCH_PATH)
                            .param("severity", "WARN", "CRITICAL")
                            .param("size", "100")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.severity == 'INFO')]").doesNotExist())
                    .andExpect(jsonPath("$.items[?(@.severity == 'WARN')]").exists())
                    .andExpect(jsonPath("$.items[?(@.severity == 'CRITICAL')]").exists());
        }
    }

    @Nested
    @DisplayName("AC-ALF-003: action + severity 복합 필터")
    class CombinedFilter {

        @Test
        @DisplayName("action=CREATE&action=UPDATE + severity=CRITICAL → 교집합만 반환")
        void combinedFilter_returnsIntersection() throws Exception {
            givenAdminToken();

            mockMvc.perform(get(SEARCH_PATH)
                            .param("action", "CREATE", "UPDATE")
                            .param("severity", "CRITICAL")
                            .param("size", "100")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    // DELETE는 없어야 함
                    .andExpect(jsonPath("$.items[?(@.action == 'DELETE')]").doesNotExist())
                    // INFO/WARN는 없어야 함
                    .andExpect(jsonPath("$.items[?(@.severity == 'INFO')]").doesNotExist())
                    .andExpect(jsonPath("$.items[?(@.severity == 'WARN')]").doesNotExist())
                    // CREATE-CRITICAL, UPDATE-CRITICAL 은 존재해야 함
                    .andExpect(jsonPath("$.items[?(@.action == 'CREATE' && @.severity == 'CRITICAL')]").exists())
                    .andExpect(jsonPath("$.items[?(@.action == 'UPDATE' && @.severity == 'CRITICAL')]").exists());
        }
    }

    @Nested
    @DisplayName("AC-ALF-004: 필터 없음 → 전체 반환 (회귀)")
    class NoFilter {

        @Test
        @DisplayName("필터 없이 조회 → total >= 9 (seed 9건)")
        void noFilter_returnsAll() throws Exception {
            givenAdminToken();

            mockMvc.perform(get(SEARCH_PATH)
                            .param("size", "100")
                            .header("Authorization", "Bearer " + VALID_TOKEN))
                    .andExpect(status().isOk())
                    // seed 9건 + 다른 IT 행 = 최소 9건
                    .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(9)));
        }
    }
}
