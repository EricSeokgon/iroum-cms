package kr.co.ircp.cms.domain.board;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-POST-MODERATE-001 게시글 어드민 모더레이션 IT.
 *
 * <p>커버 AC: AC-PA-001(목록), AC-PA-002(상태 변경), AC-PA-003(삭제), AC-PA-004(권한 가드).
 */
@AutoConfigureMockMvc
class PostAdminControllerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-post-admin-token";
    private static final String BASE_URL = "/api/v1/admin/posts";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long bbsMasterId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("post-admin-" + suffix);
        bbsMasterId = insertBbsMaster("TEST-" + suffix);
    }

    // ─── AC-PA-001 목록 조회 ──────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PA-001 목록 조회")
    class ListPosts {

        @Test
        @DisplayName("AC-PA-001: ADMIN GET → 200, HIDDEN 포함 전체 반환")
        void list_asAdmin_includesHidden() throws Exception {
            givenAdminToken();
            insertPost("공개 게시글 " + suffix, "PUBLISHED");
            insertPost("숨겨진 게시글 " + suffix, "HIDDEN");

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        @DisplayName("AC-PA-001-2: bbsId 필터 → 해당 게시판 게시글만 반환")
        void list_bbsIdFilter_returnsFiltered() throws Exception {
            givenAdminToken();
            insertPost("필터 게시글 " + suffix, "PUBLISHED");

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN)
                            .param("bbsId", String.valueOf(bbsMasterId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("AC-PA-001-3: keyword 필터 → 제목 포함 항목만 반환")
        void list_keywordFilter_returnsMatching() throws Exception {
            givenAdminToken();
            String unique = "UniqueTitle-" + suffix;
            insertPost(unique, "PUBLISHED");

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN)
                            .param("keyword", unique))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value(unique));
        }

        @Test
        @DisplayName("AC-PA-001-4: status=HIDDEN 필터 → HIDDEN만 반환")
        void list_statusFilter_returnsHiddenOnly() throws Exception {
            givenAdminToken();
            insertPost("HIDDEN 게시글 " + suffix, "HIDDEN");

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN)
                            .param("status", "HIDDEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    // ─── AC-PA-002 상태 변경 ──────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PA-002 상태 변경")
    class ChangeStatus {

        @Test
        @DisplayName("AC-PA-002: ADMIN PATCH status=HIDDEN → 200, 상태 반영")
        void changeStatus_toHidden_returns200() throws Exception {
            givenAdminToken();
            long id = insertPost("상태 변경 대상 " + suffix, "PUBLISHED");

            mockMvc.perform(patch(BASE_URL + "/" + id + "/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"HIDDEN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.status").value("HIDDEN"));
        }

        @Test
        @DisplayName("AC-PA-002-2: 존재하지 않는 id → 404")
        void changeStatus_notFound_returns404() throws Exception {
            givenAdminToken();

            mockMvc.perform(patch(BASE_URL + "/99999999/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"HIDDEN\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── AC-PA-003 삭제 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PA-003 삭제")
    class DeletePost {

        @Test
        @DisplayName("AC-PA-003: ADMIN DELETE → 204")
        void delete_asAdmin_returns204() throws Exception {
            givenAdminToken();
            long id = insertPost("삭제 대상 " + suffix, "PUBLISHED");

            mockMvc.perform(delete(BASE_URL + "/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-PA-003-2: 존재하지 않는 id → 404")
        void delete_notFound_returns404() throws Exception {
            givenAdminToken();

            mockMvc.perform(delete(BASE_URL + "/99999999")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── AC-PA-004 권한 가드 ──────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PA-004 권한 가드")
    class AuthGuard {

        @Test
        @DisplayName("AC-PA-004: 미인증 요청 → 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-PA-004-2: USER 역할 → 403")
        void userRole_returns403() throws Exception {
            givenUserToken(adminId, Set.of("USER"));

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────

    private void givenAdminToken() {
        givenUserToken(adminId, Set.of("ADMIN"));
    }

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "post-admin-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '게시글관리자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertBbsMaster(String code) {
        jdbcTemplate.update(
                "INSERT INTO bbs_master (code, name, description, type, use_comment, use_attachment, " +
                "max_attachment_count, max_attachment_size_kb, allow_anonymous, allow_secret, " +
                "page_size, role_required_read, role_required_write, status, metadata) " +
                "VALUES (?, ?, '테스트 게시판', 'NORMAL', true, false, 5, 5120, false, false, " +
                "20, null, null, 'ACTIVE', '{}'::jsonb)",
                code, "테스트게시판-" + code);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_master WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private long insertPost(String title, String status) {
        jdbcTemplate.update(
                "INSERT INTO bbs_post (bbs_id, title, content_html, content_text, " +
                "author_id, author_name, is_notice, is_secret, status) " +
                "VALUES (?, ?, '<p>내용</p>', '내용', ?, '관리자', false, false, ?)",
                bbsMasterId, title, adminId, status);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_post WHERE title = ? AND author_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, title, adminId);
        return id == null ? -1L : id;
    }
}
