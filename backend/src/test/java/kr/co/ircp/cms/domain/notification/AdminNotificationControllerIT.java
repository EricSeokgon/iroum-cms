package kr.co.ircp.cms.domain.notification;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-NC-IT-001: AdminNotificationController 통합 테스트.
 *
 * <p>커버 AC:
 * AC-NC-IT-001(목록 조회), AC-NC-IT-002(상태 필터), AC-NC-IT-003(개별 읽음),
 * AC-NC-IT-004(일괄 읽음), AC-NC-IT-005(보관), AC-NC-IT-006(미읽음 수),
 * AC-NC-IT-007(권한 가드), AC-NC-IT-008(사용자 격리).
 *
 * <p>인가: 클래스 레벨 @PreAuthorize("hasAnyRole('SUPER_ADMIN','CONTENT_ADMIN','ADMIN')").
 * notification 패키지 배치 → AuthorizationCoverageArchTest baseline(126) 에 영향 없음.
 */
// @MX:NOTE: [AUTO] AdminNotificationControllerIT — SPEC-CMS-NC-IT-001 8 AC 통합 검증
// @MX:SPEC: SPEC-CMS-NC-IT-001
@AutoConfigureMockMvc
@DisplayName("AdminNotification IT (SPEC-CMS-NC-IT-001)")
class AdminNotificationControllerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-nc-it-token";
    private static final String BASE = "/api/v1/admin/notifications";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long otherAdminId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("nc-admin-" + suffix);
        otherAdminId = insertUser("nc-other-" + suffix);
    }

    // ─── AC-NC-IT-001: 목록 조회 ─────────────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-001 목록 조회")
    class ListNotifications {

        @Test
        @DisplayName("AC-NC-IT-001: ADMIN GET /admin/notifications → 200 + Page 구조")
        void list_asAdmin_returnsPage() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "승인 요청 " + suffix, "UNREAD");
            insertNotification(adminId, "SECURITY_EVENT", "WARN", "보안 이벤트 " + suffix, "READ");

            givenAdminToken();
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN)
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").exists())
                    .andExpect(jsonPath("$.totalPages").exists());
        }

        @Test
        @DisplayName("AC-NC-IT-001-2: 응답 항목에 type/severity/title/status 포함")
        void list_includesCoreFields() throws Exception {
            insertNotification(adminId, "INTEGRATION_ERROR", "ERROR", "통합 오류 " + suffix, "UNREAD");

            givenAdminToken();
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN)
                            .param("status", "UNREAD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].type").exists())
                    .andExpect(jsonPath("$.content[0].severity").exists())
                    .andExpect(jsonPath("$.content[0].title").exists())
                    .andExpect(jsonPath("$.content[0].status").value("UNREAD"));
        }
    }

    // ─── AC-NC-IT-002: 상태 필터 ────────────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-002 상태 필터")
    class StatusFilter {

        @Test
        @DisplayName("AC-NC-IT-002-A: ?status=UNREAD → UNREAD 알림만 반환")
        void filterByUnread() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "미읽음 " + suffix, "UNREAD");
            insertNotification(adminId, "SECURITY_EVENT", "WARN", "읽음 " + suffix, "READ");

            givenAdminToken();
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN)
                            .param("status", "UNREAD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.status == 'READ')]").isEmpty());
        }

        @Test
        @DisplayName("AC-NC-IT-002-B: ?status=ARCHIVED → ARCHIVED 알림만 반환")
        void filterByArchived() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "보관됨 " + suffix, "ARCHIVED");
            insertNotification(adminId, "POLICY_SYNC", "INFO", "미읽음-필터용 " + suffix, "UNREAD");

            givenAdminToken();
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN)
                            .param("status", "ARCHIVED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.status == 'UNREAD')]").isEmpty());
        }

        @Test
        @DisplayName("AC-NC-IT-002-C: 파라미터 없으면 UNREAD+READ (ARCHIVED 제외) 기본 반환")
        void defaultExcludesArchived() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "UNREAD기본 " + suffix, "UNREAD");
            insertNotification(adminId, "SECURITY_EVENT", "INFO", "ARCHIVED기본 " + suffix, "ARCHIVED");

            givenAdminToken();
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.status == 'ARCHIVED')]").isEmpty());
        }
    }

    // ─── AC-NC-IT-003: 개별 읽음 처리 ───────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-003 개별 읽음 처리")
    class MarkRead {

        @Test
        @DisplayName("AC-NC-IT-003: PATCH /{id}/read → 204 + DB status=READ, read_at IS NOT NULL")
        void markRead_returns204_andUpdatesDb() throws Exception {
            long nid = insertNotification(adminId, "POST_APPROVAL", "INFO", "읽음 처리 " + suffix, "UNREAD");

            givenAdminToken();
            mockMvc.perform(patch(BASE + "/" + nid + "/read")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM admin_notification WHERE id = ?", String.class, nid);
            assert "READ".equals(dbStatus) : "status 는 READ 이어야 함 (실제: " + dbStatus + ")";

            Integer readAtCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_notification WHERE id = ? AND read_at IS NOT NULL",
                    Integer.class, nid);
            assert readAtCount != null && readAtCount == 1 : "read_at 이 기록되어야 함";
        }
    }

    // ─── AC-NC-IT-004: 일괄 읽음 처리 ──────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-004 일괄 읽음 처리")
    class MarkAllRead {

        @Test
        @DisplayName("AC-NC-IT-004: PATCH /read-all → 200 + updatedCount, 모든 UNREAD → READ")
        void markAllRead_returnsUpdatedCount() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "일괄-1 " + suffix, "UNREAD");
            insertNotification(adminId, "SECURITY_EVENT", "WARN", "일괄-2 " + suffix, "UNREAD");
            // 이미 READ 는 카운트 안 됨
            insertNotification(adminId, "POLICY_SYNC", "INFO", "기읽음 " + suffix, "READ");

            givenAdminToken();
            mockMvc.perform(patch(BASE + "/read-all")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updatedCount").value(2));

            Integer remaining = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_notification WHERE admin_user_id = ? AND status = 'UNREAD'",
                    Integer.class, adminId);
            assert remaining != null && remaining == 0 : "UNREAD 알림이 남아 있으면 안 됨 (실제: " + remaining + ")";
        }
    }

    // ─── AC-NC-IT-005: 보관 처리 ────────────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-005 보관 처리")
    class Archive {

        @Test
        @DisplayName("AC-NC-IT-005: PATCH /{id}/archive → 204 + DB status=ARCHIVED, archived_at IS NOT NULL")
        void archive_returns204_andUpdatesDb() throws Exception {
            long nid = insertNotification(adminId, "INTEGRATION_ERROR", "ERROR", "보관 대상 " + suffix, "UNREAD");

            givenAdminToken();
            mockMvc.perform(patch(BASE + "/" + nid + "/archive")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM admin_notification WHERE id = ?", String.class, nid);
            assert "ARCHIVED".equals(dbStatus) : "status 는 ARCHIVED 이어야 함 (실제: " + dbStatus + ")";

            Integer archivedAtCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admin_notification WHERE id = ? AND archived_at IS NOT NULL",
                    Integer.class, nid);
            assert archivedAtCount != null && archivedAtCount == 1 : "archived_at 이 기록되어야 함";
        }
    }

    // ─── AC-NC-IT-006: 미읽음 수 ────────────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-006 미읽음 수")
    class UnreadCount {

        @Test
        @DisplayName("AC-NC-IT-006: GET /unread-count → 200 + {unreadCount: N}")
        void unreadCount_returnsCorrectValue() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "미읽음A " + suffix, "UNREAD");
            insertNotification(adminId, "SECURITY_EVENT", "WARN", "미읽음B " + suffix, "UNREAD");
            insertNotification(adminId, "POLICY_SYNC", "INFO", "읽음 " + suffix, "READ");

            givenAdminToken();
            mockMvc.perform(get(BASE + "/unread-count")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unreadCount").value(2));
        }
    }

    // ─── AC-NC-IT-007: 권한 가드 ────────────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-007 권한 가드")
    class Authorization {

        @Test
        @DisplayName("AC-NC-IT-007-A: 비인증 GET → 401")
        void unauthenticated_returns401() throws Exception {
            int code = mockMvc.perform(get(BASE))
                    .andReturn().getResponse().getStatus();
            assert code == 401 : "비인증 GET 응답은 401 이어야 함 (실제: " + code + ")";
        }

        @Test
        @DisplayName("AC-NC-IT-007-B: USER 권한 → 403")
        void userRole_returns403() throws Exception {
            givenUserToken(adminId, Set.of("USER"));
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-NC-IT-007-C: CONTENT_ADMIN 권한 → 200 허용")
        void contentAdminRole_returns200() throws Exception {
            givenUserToken(adminId, Set.of("CONTENT_ADMIN"));
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN)
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk());
        }
    }

    // ─── AC-NC-IT-008: 사용자 격리 ──────────────────────────────────────────

    @Nested
    @DisplayName("AC-NC-IT-008 사용자 격리")
    class UserIsolation {

        @Test
        @DisplayName("AC-NC-IT-008: 타인의 알림에 read 요청 → 403 (REQ-NC-010 열거 방지)")
        void readOtherUserNotification_returns403() throws Exception {
            // otherAdminId 소유 알림을 adminId 로 읽음 처리 시도
            // 서비스는 ensureOwned 에서 AdminNotificationNotFoundException → GlobalExceptionHandler → 403
            long nid = insertNotification(otherAdminId, "POST_APPROVAL", "INFO",
                    "타인알림 " + suffix, "UNREAD");

            givenAdminToken(); // adminId 토큰
            mockMvc.perform(patch(BASE + "/" + nid + "/read")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());

            // DB 상태 불변 검증
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM admin_notification WHERE id = ?", String.class, nid);
            assert "UNREAD".equals(dbStatus)
                    : "타인 알림 status 는 UNREAD 여야 함 (실제: " + dbStatus + ")";
        }

        @Test
        @DisplayName("AC-NC-IT-008-2: 목록 조회 시 본인 알림만 반환")
        void listOnlyOwnNotifications() throws Exception {
            insertNotification(adminId, "POST_APPROVAL", "INFO", "내알림 " + suffix, "UNREAD");
            insertNotification(otherAdminId, "SECURITY_EVENT", "WARN", "타인알림 " + suffix, "UNREAD");

            givenAdminToken();
            mockMvc.perform(get(BASE)
                            .header("Authorization", TOKEN)
                            .param("status", "UNREAD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.title == '타인알림 " + suffix + "')]").isEmpty());
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private void givenAdminToken() {
        givenUserToken(adminId, Set.of("ADMIN"));
    }

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "nc-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '알림테스트관리자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    /** admin_notification 직접 INSERT. ARCHIVED/READ 상태 시 타임스탬프 자동 설정. */
    private long insertNotification(long userId, String type, String severity,
                                    String title, String status) {
        if ("ARCHIVED".equals(status)) {
            jdbcTemplate.update(
                    "INSERT INTO admin_notification (admin_user_id, type, severity, title, status, " +
                    "read_at, archived_at, created_at) " +
                    "VALUES (?, ?, ?, ?, 'ARCHIVED', NOW(), NOW(), NOW())",
                    userId, type, severity, title);
        } else if ("READ".equals(status)) {
            jdbcTemplate.update(
                    "INSERT INTO admin_notification (admin_user_id, type, severity, title, status, " +
                    "read_at, created_at) " +
                    "VALUES (?, ?, ?, ?, 'READ', NOW(), NOW())",
                    userId, type, severity, title);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO admin_notification (admin_user_id, type, severity, title, status, " +
                    "created_at) VALUES (?, ?, ?, ?, 'UNREAD', NOW())",
                    userId, type, severity, title);
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_notification WHERE admin_user_id = ? AND title = ? " +
                "ORDER BY id DESC LIMIT 1",
                Long.class, userId, title);
        return id == null ? -1L : id;
    }
}
