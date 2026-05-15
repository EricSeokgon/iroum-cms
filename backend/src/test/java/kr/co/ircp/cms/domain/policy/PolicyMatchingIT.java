package kr.co.ircp.cms.domain.policy;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-007 §A~§E 정책 도메인 통합 테스트.
 *
 * <p>실제 PostgreSQL 16 + Flyway 마이그레이션 스키마 상에서 5개 컨트롤러 흐름을 검증한다:
 * <ul>
 *   <li>§REQ-POLICY-001 정책프로그램 CRUD (PolicyProgramController)</li>
 *   <li>§REQ-POLICY-002 기업 프로필 + 매칭 + 캐시 (PolicyMatchingController)</li>
 *   <li>§REQ-POLICY-003 발송 예약 (PolicyDispatchController)</li>
 *   <li>§REQ-POLICY-004 알림 수신 구독 (PolicyNotificationSubscriptionController)</li>
 *   <li>§REQ-POLICY-005 추적 + 전환률 통계 (PolicyTrackingController)</li>
 * </ul>
 *
 * <p>인증: JwtAuthenticationFilter 우회를 위해 {@link JwtTokenProvider} 와
 * {@link TokenBlacklistMapper} 를 MockitoBean 으로 주입, ADMIN 토큰을 위조한다.
 *
 * <p>NOTE: 정책 도메인 컨트롤러는 {@code @AuthenticationPrincipal} 을 사용하지 않고
 * 모두 {@code @RequestParam companyId/userId} 로 사용자 컨텍스트를 받기 때문에
 * 실 DB 데이터로 happy-path 전 영역을 검증할 수 있다.
 */
// @MX:NOTE: [AUTO] PolicyMatchingIT — SPEC-CMS-007 §A~§E IT (정책프로그램·매칭·발송·구독·추적 전 영역)
// @MX:SPEC: SPEC-CMS-007
@AutoConfigureMockMvc
@DisplayName("정책 도메인 IT (SPEC-CMS-007 §A~§E)")
class PolicyMatchingIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-policy-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    /** 테스트 격리를 위한 식별자 접미사. */
    private String suffix;

    @BeforeEach
    void setUp() {
        // 1) 테스트 격리용 접미사
        suffix = UUID.randomUUID().toString().substring(0, 8);

        // 2) FK 순서를 존중한 정책 도메인 테이블 정리
        //    notification dispatch → policy matching → policy program 관련 → subscription → template
        jdbcTemplate.update("DELETE FROM notification_dispatch_target");
        jdbcTemplate.update("DELETE FROM notification_dispatch_schedule");
        jdbcTemplate.update("DELETE FROM policy_match_score");
        jdbcTemplate.update("DELETE FROM company_match_input");
        jdbcTemplate.update("DELETE FROM policy_application_log");
        jdbcTemplate.update("DELETE FROM policy_eligibility_rule");
        jdbcTemplate.update("DELETE FROM policy_keyword");
        jdbcTemplate.update("DELETE FROM policy_program");
        jdbcTemplate.update("DELETE FROM notification_subscription");
        jdbcTemplate.update("DELETE FROM notification_template");
        // NOTE: users 테이블은 다른 IT 가 의존할 수 있으므로 삭제하지 않음.
        //       대신 username 에 unique suffix 를 부여하여 충돌 회피.

        // 3) ADMIN 사용자 + 토큰 준비
        adminId = insertUser("policy-admin-" + suffix);
        givenAdminToken();
    }

    // ─── §REQ-POLICY-001 정책프로그램 CRUD ────────────────────────────────────

    @Nested
    @DisplayName("§REQ-POLICY-001 정책프로그램 CRUD")
    class Programs {

        @Test
        @DisplayName("REQ-POLICY-001: GET /programs (비인증) → 401")
        void listPrograms_withoutAuth_returnsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/policy/programs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("REQ-POLICY-001-D-5: POST /admin/programs (ADMIN) → 201 + id/programName")
        void createProgram_asAdmin_returns201() throws Exception {
            String code = "PROG-IT-" + suffix;
            String body = """
                    {
                      "code": "%s",
                      "ministry": "중소벤처기업부",
                      "programName": "IT 테스트 정책",
                      "status": "ACTIVE",
                      "applicationStart": "2026-01-01T00:00:00Z",
                      "applicationEnd": "2026-12-31T23:59:59Z"
                    }
                    """.formatted(code);

            mockMvc.perform(post("/api/v1/policy/admin/programs")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.programName").value("IT 테스트 정책"));
        }

        @Test
        @DisplayName("REQ-POLICY-001-D-1: GET /programs (ADMIN) → 200 + content")
        void listPrograms_asAdmin_returnsOk() throws Exception {
            // 사전: 1건 INSERT
            insertPolicyProgram();

            mockMvc.perform(get("/api/v1/policy/programs")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").exists());
        }

        @Test
        @DisplayName("REQ-POLICY-001-D-2: GET /programs/{id} (ADMIN) → 200 + code")
        void getProgramById_asAdmin_returnsOk() throws Exception {
            long programId = insertPolicyProgram();

            mockMvc.perform(get("/api/v1/policy/programs/" + programId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }

        @Test
        @DisplayName("REQ-POLICY-001-D-5: PUT /admin/programs/{id} (ADMIN) → 200")
        void updateProgram_asAdmin_returnsOk() throws Exception {
            long programId = insertPolicyProgram();
            String body = """
                    {
                      "programName": "수정된 정책명",
                      "status": "ACTIVE"
                    }
                    """;

            mockMvc.perform(put("/api/v1/policy/admin/programs/" + programId)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }

    // ─── §REQ-POLICY-002 매칭 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("§REQ-POLICY-002 기업 프로필 + 매칭")
    class Matching {

        @Test
        @DisplayName("REQ-POLICY-002-D: PUT /company-profile (ADMIN) → 204")
        void upsertCompanyProfile_asAdmin_returnsNoContent() throws Exception {
            String body = """
                    {
                      "companyId": %d,
                      "industryCodes": ["IT"],
                      "regionCodes": ["SEOUL"],
                      "employeeCount": 10
                    }
                    """.formatted(adminId);

            mockMvc.perform(put("/api/v1/policy/company-profile")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("REQ-POLICY-002: POST /match?companyId=&topN=5 (ADMIN) → 200")
        void match_asAdmin_returnsOk() throws Exception {
            // 사전: 매칭 대상 프로필 + 정책 프로그램 필요
            upsertProfile(adminId);
            insertPolicyProgram();

            mockMvc.perform(post("/api/v1/policy/match")
                            .header("Authorization", TOKEN)
                            .param("companyId", String.valueOf(adminId))
                            .param("topN", "5"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("REQ-POLICY-002-D-5: GET /match/results (ADMIN, 캐시) → 200")
        void getMatchResults_asAdmin_returnsOk() throws Exception {
            // 사전: 매칭 1회 실행 → 캐시 적재
            upsertProfile(adminId);
            insertPolicyProgram();
            mockMvc.perform(post("/api/v1/policy/match")
                            .header("Authorization", TOKEN)
                            .param("companyId", String.valueOf(adminId))
                            .param("topN", "5"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/policy/match/results")
                            .header("Authorization", TOKEN)
                            .param("companyId", String.valueOf(adminId))
                            .param("topN", "5"))
                    .andExpect(status().isOk());
        }
    }

    // ─── §REQ-POLICY-003 발송 예약 ───────────────────────────────────────────

    @Nested
    @DisplayName("§REQ-POLICY-003 발송 예약")
    class Dispatch {

        @Test
        @DisplayName("REQ-POLICY-003: GET /admin/dispatch/schedules (ADMIN) → 200")
        void listSchedules_asAdmin_returnsOk() throws Exception {
            mockMvc.perform(get("/api/v1/policy/admin/dispatch/schedules")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("REQ-POLICY-003-D-1: POST /admin/dispatch/schedules (ADMIN) → 201")
        void createSchedule_asAdmin_returns201() throws Exception {
            long templateId = insertNotificationTemplate();
            String body = """
                    {
                      "dispatchType": "ANNOUNCEMENT",
                      "scheduledAt": "2026-12-01T09:00:00Z",
                      "channels": ["EMAIL"],
                      "templateId": %d,
                      "createdBy": %d,
                      "priority": 1
                    }
                    """.formatted(templateId, adminId);

            mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("REQ-POLICY-003-D: POST /admin/dispatch/schedules/{id}/cancel (ADMIN) → 204")
        void cancelSchedule_asAdmin_returnsNoContent() throws Exception {
            // 사전: 발송 예약 1건 생성
            long templateId = insertNotificationTemplate();
            long scheduleId = createDispatchSchedule(templateId);

            mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules/" + scheduleId + "/cancel")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }
    }

    // ─── §REQ-POLICY-004 알림 수신 구독 ──────────────────────────────────────

    @Nested
    @DisplayName("§REQ-POLICY-004 알림 수신 구독")
    class Subscriptions {

        @Test
        @DisplayName("REQ-POLICY-004: PUT /subscriptions/me (ADMIN) → 204")
        void updateMySubscriptions_asAdmin_returnsNoContent() throws Exception {
            String body = """
                    {
                      "entries": [
                        {"channel": "EMAIL", "category": "POLICY_MATCH", "optedIn": true}
                      ]
                    }
                    """;

            mockMvc.perform(put("/api/v1/policy/subscriptions/me")
                            .header("Authorization", TOKEN)
                            .param("userId", String.valueOf(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("REQ-POLICY-004-D: GET /subscriptions/me (ADMIN) → 200")
        void getMySubscriptions_asAdmin_returnsOk() throws Exception {
            mockMvc.perform(get("/api/v1/policy/subscriptions/me")
                            .header("Authorization", TOKEN)
                            .param("userId", String.valueOf(adminId)))
                    .andExpect(status().isOk());
        }
    }

    // ─── §REQ-POLICY-005 추적 + 전환률 통계 ──────────────────────────────────

    @Nested
    @DisplayName("§REQ-POLICY-005 추적 + 전환률 통계")
    class Tracking {

        @Test
        @DisplayName("REQ-POLICY-005: POST /programs/{id}/track (ADMIN) → 204")
        void trackEvent_asAdmin_returnsNoContent() throws Exception {
            long programId = insertPolicyProgram();
            String body = """
                    {
                      "source": "DIRECT",
                      "action": "VIEW"
                    }
                    """;

            mockMvc.perform(post("/api/v1/policy/programs/" + programId + "/track")
                            .header("Authorization", TOKEN)
                            .param("userId", String.valueOf(adminId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("REQ-POLICY-005-D-4: GET /admin/stats/conversion (ADMIN) → 200")
        void getConversionStats_asAdmin_returnsOk() throws Exception {
            long programId = insertPolicyProgram();

            mockMvc.perform(get("/api/v1/policy/admin/stats/conversion")
                            .header("Authorization", TOKEN)
                            .param("policyId", String.valueOf(programId)))
                    .andExpect(status().isOk());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** ADMIN 토큰 스텁 (JwtAuthenticationFilter 우회). */
    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "policy-admin-" + adminId,
                Set.of("ADMIN", "SUPER_ADMIN"), Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    /** 테스트용 사용자 INSERT (다른 IT 와 충돌하지 않도록 unique username). */
    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '정책테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    /** 정책프로그램 1건 INSERT 후 id 반환. */
    private long insertPolicyProgram() {
        String code = "PP-" + uid();
        jdbcTemplate.update(
                "INSERT INTO policy_program (code, ministry, program_name, status) " +
                        "VALUES (?, '중소벤처기업부', '테스트정책', 'ACTIVE')",
                code);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM policy_program WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    /** 알림 템플릿 1건 INSERT 후 id 반환 (발송 예약 테스트 선행 조건). */
    private long insertNotificationTemplate() {
        String code = "TMPL-" + uid();
        jdbcTemplate.update(
                "INSERT INTO notification_template (code, name, channel, body_template) " +
                        "VALUES (?, ?, 'EMAIL', '테스트 템플릿')",
                code, "테스트 알림 템플릿");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_template WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    /** 기업 프로필 등록 헬퍼 (매칭 테스트 선행 조건). */
    private void upsertProfile(long companyId) throws Exception {
        String body = """
                {
                  "companyId": %d,
                  "industryCodes": ["IT"],
                  "regionCodes": ["SEOUL"],
                  "employeeCount": 10
                }
                """.formatted(companyId);
        mockMvc.perform(put("/api/v1/policy/company-profile")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    /** 발송 예약 1건 생성 후 id 반환 (cancel 테스트 선행 조건). */
    private long createDispatchSchedule(long templateId) throws Exception {
        String body = """
                {
                  "dispatchType": "ANNOUNCEMENT",
                  "scheduledAt": "2026-12-01T09:00:00Z",
                  "channels": ["EMAIL"],
                  "templateId": %d,
                  "createdBy": %d,
                  "priority": 1
                }
                """.formatted(templateId, adminId);
        mockMvc.perform(post("/api/v1/policy/admin/dispatch/schedules")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        // notification_dispatch_schedule 의 가장 최근 row id 반환
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM notification_dispatch_schedule ORDER BY id DESC LIMIT 1",
                Long.class);
        return id == null ? -1L : id;
    }

    /** 짧은 unique 식별자. */
    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
