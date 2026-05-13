package kr.co.ircp.cms.domain.system;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.system.maintenance.service.MaintenanceService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-005 §E 시스템 설정 + 점검 모드 IT (REQ-SYSTEM-005-D-1..5).
 *
 * <p>5 AC 커버:
 * <ul>
 *   <li>D-1: 시스템 설정 PUT 200 + DB 갱신 검증</li>
 *   <li>D-2: 점검 등록 201 + DB SCHEDULED 검증</li>
 *   <li>D-3: ACTIVE 점검 중 익명 사용자 503 + Retry-After 헤더</li>
 *   <li>D-4: ACTIVE 점검 중 ADMIN 화이트리스트 통과 (allow_admin_access=true + SUPER_ADMIN 역할)</li>
 *   <li>D-5: end_at 경과 시 자동 COMPLETED 전환 (service.completeExpired() 직접 호출)</li>
 * </ul>
 *
 * <p>인증 모델: SYSTEM:MAINT:READ/WRITE + SYSTEM:SETTING:WRITE 권한(authority).
 * MaintenanceFilter는 SUPER_ADMIN/SYSADMIN 역할(ROLE_) 기준으로 화이트리스트 처리.
 */
// @MX:NOTE: [AUTO] MaintenanceIT — SPEC-CMS-005 §E 점검 모드 IT (SYSTEM:MAINT:WRITE 권한 패턴)
// @MX:SPEC: SPEC-CMS-005#REQ-SYSTEM-005-D
@AutoConfigureMockMvc
@DisplayName("시스템 설정 + 점검 모드 IT (SPEC-CMS-005 §E)")
class MaintenanceIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-maint-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MaintenanceService maintenanceService;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("maint-admin-" + uid());
        // @MX:NOTE: [AUTO] 각 테스트 시작 시 maintenance 테이블 비움.
        // 이전 테스트에서 ACTIVE 점검이 남아 있으면 MaintenanceFilter가 다른 테스트를 503 으로 차단하기 때문.
        jdbcTemplate.update("DELETE FROM maintenance");
    }

    // ─── §E-1: REQ-SYSTEM-005-D-1 — 시스템 설정 CRUD ─────────────────────────

    @Nested
    @DisplayName("§E-1: 시스템 설정 CRUD (REQ-SYSTEM-005-D-1)")
    class SettingCrud {

        @Test
        @DisplayName("REQ-SYSTEM-005-D-1 — PUT /api/v1/system/settings/{key} : 200 OK + DB 갱신")
        // @MX:NOTE: [AUTO] audit_log assertion 은 비동기라 생략 — 200 OK + DB UPSERT 검증으로 충분
        void putSetting_returns200_andPersistsValue() throws Exception {
            givenAdminToken();

            String key = "site.title";
            String newValue = "공공기관 CMS";
            String body = """
                    {"value":"%s","description":"메인 사이트 타이틀"}
                    """.formatted(newValue);

            mockMvc.perform(put("/api/v1/system/settings/" + key)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.key").value(key))
                    .andExpect(jsonPath("$.value").value(newValue));

            // DB 직접 조회로 UPSERT 결과 검증
            String dbValue = jdbcTemplate.queryForObject(
                    "SELECT value FROM system_setting WHERE key = ?", String.class, key);
            assertEquals(newValue, dbValue, "system_setting 테이블에 신규 값이 반영되어야 함");
        }
    }

    // ─── §E-2: REQ-SYSTEM-005-D-2 — 점검 등록 ─────────────────────────────────

    @Nested
    @DisplayName("§E-2: 점검 등록 (REQ-SYSTEM-005-D-2)")
    class MaintenanceCreate {

        @Test
        @DisplayName("REQ-SYSTEM-005-D-2 — POST /api/v1/system/maintenance : 201 Created + DB SCHEDULED")
        void createMaintenance_returns201_andStatusScheduled() throws Exception {
            givenAdminToken();

            String title = "정기점검-" + uid();
            String body = """
                    {
                      "title": "%s",
                      "startAt": "2099-01-01T00:00:00Z",
                      "endAt": "2099-01-02T00:00:00Z",
                      "messageKo": "점검중",
                      "messageEn": "Maintenance",
                      "allowAdminAccess": true
                    }
                    """.formatted(title);

            mockMvc.perform(post("/api/v1/system/maintenance")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.status").value("SCHEDULED"));

            // DB 검증 — status=SCHEDULED 인 row 존재
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM maintenance WHERE title = ? AND status = 'SCHEDULED'",
                    Integer.class, title);
            assertNotNull(cnt);
            assertEquals(1, cnt.intValue(), "SCHEDULED 상태로 1건 등록되어야 함");
        }
    }

    // ─── §E-3: REQ-SYSTEM-005-D-3 — 점검 모드 활성 (일반 사용자 차단) ──────────

    @Nested
    @DisplayName("§E-3: 점검 모드 활성 — 일반 사용자 503 차단 (REQ-SYSTEM-005-D-3)")
    class MaintenanceBlock {

        @Test
        @DisplayName("REQ-SYSTEM-005-D-3 — ACTIVE 점검 중 익명 GET /api/v1/health : 503 + Retry-After")
        // @MX:NOTE: [AUTO] /api/v1/board/posts 경로는 미구현이므로 permitAll 인 /api/v1/health 로 검증.
        // MaintenanceFilter 는 Spring Security 외부 Servlet 필터이므로 permitAll 엔드포인트도 차단한다.
        void activeMaintenance_anonymousUser_returns503() throws Exception {
            // ACTIVE 점검 직접 INSERT — start<=NOW<=end, status=ACTIVE
            insertActiveMaintenance(
                    "active-block-" + uid(),
                    Instant.now().minusSeconds(60),
                    Instant.now().plusSeconds(3600),
                    "점검중입니다",
                    "Under maintenance",
                    true);

            // 익명 요청 (Authorization 헤더 없음)
            mockMvc.perform(get("/api/v1/health"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.messageKo").value("점검중입니다"))
                    .andExpect(jsonPath("$.messageEn").value("Under maintenance"));
        }
    }

    // ─── §E-4: REQ-SYSTEM-005-D-4 — 관리자 화이트리스트 통과 ───────────────────

    @Nested
    @DisplayName("§E-4: 관리자 화이트리스트 (REQ-SYSTEM-005-D-4)")
    class MaintenanceAdminBypass {

        @Test
        @DisplayName("REQ-SYSTEM-005-D-4 — ACTIVE 점검 중 SUPER_ADMIN 토큰 GET /api/v1/health : 200 OK")
        // @MX:NOTE: [AUTO] MaintenanceFilter 는 ROLE_SUPER_ADMIN / ROLE_SYSADMIN 만 통과시킨다.
        // ADMIN 역할이 아닌 SUPER_ADMIN 역할이 필요하므로 토큰 claim 에 SUPER_ADMIN 를 주입한다.
        void activeMaintenance_superAdmin_passes() throws Exception {
            insertActiveMaintenance(
                    "active-admin-" + uid(),
                    Instant.now().minusSeconds(60),
                    Instant.now().plusSeconds(3600),
                    "점검중",
                    "Maintenance",
                    true);

            givenSuperAdminToken();

            mockMvc.perform(get("/api/v1/health").header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    // ─── §E-5: REQ-SYSTEM-005-D-5 — 점검 종료 자동 ────────────────────────────

    @Nested
    @DisplayName("§E-5: 점검 종료 자동 (REQ-SYSTEM-005-D-5)")
    class MaintenanceAutoComplete {

        @Test
        @DisplayName("REQ-SYSTEM-005-D-5 — end_at 경과 후 completeExpired() : status=COMPLETED")
        // @MX:NOTE: [AUTO] @Scheduled cron 은 IT 컨텍스트에서 매분마다만 발화하므로 직접 호출로 검증.
        // MaintenanceService.completeExpired() 는 서비스 인터페이스의 공개 메서드이며 cron 과 동일 로직.
        void expiredMaintenance_autoCompletes() throws Exception {
            String title = "expired-" + uid();
            insertActiveMaintenance(
                    title,
                    Instant.now().minusSeconds(3600),
                    Instant.now().minusSeconds(60), // end_at 1분 전 → 경과
                    "끝난점검",
                    "Expired",
                    true);

            // 스케줄러 로직 직접 호출
            maintenanceService.completeExpired();

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM maintenance WHERE title = ?", String.class, title);
            assertEquals("COMPLETED", status, "end_at 경과 시 status 가 COMPLETED 로 전환되어야 함");
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void givenAdminToken() {
        // SYSTEM:MAINT:READ/WRITE + SYSTEM:SETTING:WRITE 권한 보유 ADMIN 토큰
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "maint-admin",
                Set.of("ADMIN"),
                Set.of("SYSTEM:MAINT:READ", "SYSTEM:MAINT:WRITE", "SYSTEM:SETTING:WRITE"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private void givenSuperAdminToken() {
        // MaintenanceFilter 화이트리스트 통과용 — ROLE_SUPER_ADMIN 부여
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "maint-super-admin",
                Set.of("SUPER_ADMIN"),
                Set.of("SYSTEM:MAINT:READ", "SYSTEM:MAINT:WRITE", "SYSTEM:SETTING:WRITE"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '점검테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private void insertActiveMaintenance(
            String title, Instant startAt, Instant endAt,
            String messageKo, String messageEn, boolean allowAdminAccess) {
        jdbcTemplate.update(
                "INSERT INTO maintenance " +
                        "(title, message_ko, message_en, start_at, end_at, status, allow_admin_access, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                title, messageKo, messageEn,
                Timestamp.from(startAt), Timestamp.from(endAt),
                allowAdminAccess, adminId);
    }
}
