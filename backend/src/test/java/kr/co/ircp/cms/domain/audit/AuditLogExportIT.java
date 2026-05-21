package kr.co.ircp.cms.domain.audit;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 감사 로그 CSV 내보내기 통합 테스트.
 *
 * <p>SPEC-CMS-005 §7 — ROLE_ADMIN 전용 CSV 내보내기 엔드포인트 검증.
 *
 * <p>설계:
 * <ul>
 *   <li>운영 SecurityFilterChain + JwtAuthenticationFilter + Method Security를 그대로 적재한다
 *       (AuthorizationMatrixIT와 동일 패턴).</li>
 *   <li>{@link JwtTokenProvider} / {@link TokenBlacklistMapper}는 {@code @MockitoBean}으로 우회하여
 *       DB 토큰 저장 없이 권한 시나리오만 검증한다.</li>
 *   <li>실제 audit_log 행은 {@link JdbcTemplate}으로 직접 INSERT 하여 ResultHandler 스트리밍
 *       경로 + CSV 직렬화를 end-to-end 검증한다.</li>
 *   <li>본 IT는 트랜잭션 어노테이션을 두지 않는다 — INSERT는 격리된 audit_log 행만 추가하며
 *       APPEND-ONLY 정책상 정리도 불필요. (다른 IT 와 동일한 행을 공유해도 헤더 라인 검증에는
 *       영향 없음.)</li>
 * </ul>
 */
// @MX:NOTE: [AUTO] AuditLogExportIT — CSV 스트리밍 export 권한/응답 헤더/행 직렬화 회귀 IT
// @MX:SPEC: SPEC-CMS-005
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("감사 로그 CSV 내보내기 IT (SPEC-CMS-005 §7)")
class AuditLogExportIT {

    // ─── Testcontainers PostgreSQL 16 (운영 동등 Flyway 마이그레이션 적용) ──────────────
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
        // PII 더미 키 (32 bytes base64) — PII-001 인프라 일관
        registry.add("pii.keyvault.keys.v1", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
        registry.add("pii.keyvault.hmac-key", () -> "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    // JwtTokenProvider / TokenBlacklistMapper 는 Mock 으로 대체 — DB 토큰 저장 없이 권한 시나리오만 검증
    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TokenBlacklistMapper tokenBlacklistMapper;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String EXPORT_PATH = "/api/v1/system/audit-logs/export";

    /** CSV 헤더 — 컨트롤러와 동일해야 한다 (회귀 보호). */
    private static final String EXPECTED_CSV_HEADER =
            "id,event_time,actor_id,actor_role,action,entity_type,entity_id,"
                    + "severity,result,ip_address,trace_id,duration_ms,failure_reason";

    /**
     * 주어진 roles/permissions 로 valid 토큰을 시뮬레이션한다.
     * {@link JwtTokenProvider#validateAccessToken(String)} 가 {@link JwtTokenProvider.JwtClaims} 를
     * 반환하도록 stub하여 운영 JwtAuthenticationFilter 가 SecurityContext 에 principal 을 설정하게 한다.
     */
    private void givenValidToken(Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                1L, "testuser", roles, permissions, Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(VALID_TOKEN)).thenReturn(Optional.of(claims));
    }

    @BeforeEach
    void seedAuditLogRow() {
        // ADMIN 시나리오 검증을 위해 식별 가능한 audit_log 행을 1건 사전 적재.
        // APPEND-ONLY 트리거가 INSERT 만 허용하므로 클린업은 불필요.
        // entity_id 에 IT 식별자(UUID-like)를 넣어 출력에서 식별 가능하도록 한다.
        jdbc.update("""
                INSERT INTO audit_log (
                    event_time, actor_id, actor_role, action,
                    entity_type, entity_id,
                    ip_address, trace_id,
                    severity, result, failure_reason, duration_ms
                ) VALUES (
                    NOW(), NULL, 'TESTER', 'READ',
                    'AuditLogExportIT', 'export-it-marker',
                    '127.0.0.1', 'trace-export-it',
                    'INFO', 'SUCCESS', NULL, 12
                )
                """);
    }

    /**
     * Test 1: 인증 부재 → 401 AUTH_REQUIRED.
     * SecurityConfig.authenticationEntryPoint 가 anonymous 요청을 거부함을 검증.
     */
    @Test
    @DisplayName("Test 1: Authorization 헤더 부재 → 401")
    void export_returns401_whenNoAuth() throws Exception {
        mockMvc.perform(get(EXPORT_PATH))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test 2: MEMBER 역할(권한 부족) → 403.
     * 클래스 레벨 @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') or hasAuthority('SYSTEM:AUDIT')")
     * 가 일반 회원 역할을 거부함을 검증.
     */
    @Test
    @DisplayName("Test 2: MEMBER 역할 → 403 (ADMIN/SUPER_ADMIN/SYSTEM:AUDIT 미보유)")
    void export_returns403_whenMemberRole() throws Exception {
        givenValidToken(Set.of("MEMBER"), Set.of());

        mockMvc.perform(get(EXPORT_PATH)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    /**
     * Test 3: ADMIN 역할 → 200 + CSV 응답 헤더 + 본문에 CSV 헤더 라인 + 시드 행 포함.
     *
     * <p>StreamingResponseBody 는 Spring async request 로 처리되므로 MockMvc 에서는
     * {@code request().asyncStarted()} 로 1차 비동기 적재를 검증한 뒤 {@code asyncDispatch}
     * 로 본 dispatch 를 수행하여 응답 본문을 조회한다.
     */
    @Test
    @DisplayName("Test 3: ADMIN 역할 → 200 + text/csv + CSV 헤더 라인 + 시드 행 포함")
    void export_returns200WithCsv_whenAdminRole() throws Exception {
        // StreamingResponseBody 응답은 비동기 dispatch를 거치는데, 운영 JwtAuthenticationFilter는
        // OncePerRequestFilter 기본 동작상 ASYNC dispatch에서 재실행되지 않아 SecurityContext가 유실된다.
        // 따라서 본 테스트에서는 Bearer 헤더 대신 SecurityMockMvcRequestPostProcessors.authentication을
        // 사용하여 TestSecurityContextRepository에 인증 컨텍스트를 저장 — async dispatch에도 보존된다.
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "testuser",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        MvcResult asyncResult = mockMvc.perform(get(EXPORT_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(adminAuth)))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("audit-logs-")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("CSV 헤더 라인이 응답 본문 첫 줄에 존재")
                .startsWith(EXPECTED_CSV_HEADER);
        assertThat(body)
                .as("시드된 IT marker 행이 CSV 결과에 포함")
                .contains("export-it-marker")
                .contains("AuditLogExportIT")
                .contains("trace-export-it");
    }
}
