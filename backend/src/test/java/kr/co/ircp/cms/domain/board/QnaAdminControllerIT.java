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
 * SPEC-CMS-QNA-MODERATE-001 Q&A 어드민 모더레이션 IT.
 *
 * <p>커버 AC: AC-QNA-ADM-001(목록 조회), AC-QNA-ADM-002(상태 변경),
 * AC-QNA-ADM-003(삭제), AC-QNA-ADM-004(권한 가드).
 */
@AutoConfigureMockMvc
class QnaAdminControllerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-qna-admin-token";
    private static final String BASE_URL = "/api/v1/admin/qnas";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("qna-admin-" + suffix);
    }

    // ─── AC-QNA-ADM-001 목록 조회 ──────────────────────────────────────────

    @Nested
    @DisplayName("AC-QNA-ADM-001 목록 조회")
    class ListQnas {

        @Test
        @DisplayName("AC-QNA-ADM-001: ADMIN GET → 200, HIDDEN 포함 전체 반환")
        void list_asAdmin_includesHidden() throws Exception {
            givenAdminToken();
            insertQna("일반 질문 " + suffix, "PENDING", false);
            insertQna("숨겨진 질문 " + suffix, "HIDDEN", false);

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        @DisplayName("AC-QNA-ADM-001-2: status=HIDDEN 필터 → HIDDEN만 반환")
        void list_statusFilter_returnsFiltered() throws Exception {
            givenAdminToken();
            insertQna("HIDDEN 질문 " + suffix, "HIDDEN", false);

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN)
                            .param("status", "HIDDEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("AC-QNA-ADM-001-3: keyword 필터 → 제목 포함 항목만 반환")
        void list_keywordFilter_returnsMatching() throws Exception {
            givenAdminToken();
            String unique = "UniqueKeyword-" + suffix;
            insertQna(unique, "PENDING", false);

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN)
                            .param("keyword", unique))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value(unique));
        }
    }

    // ─── AC-QNA-ADM-002 상태 변경 ──────────────────────────────────────────

    @Nested
    @DisplayName("AC-QNA-ADM-002 상태 변경")
    class ChangeStatus {

        @Test
        @DisplayName("AC-QNA-ADM-002: ADMIN PATCH status=HIDDEN → 200, 상태 반영")
        void changeStatus_asAdmin_returns200() throws Exception {
            givenAdminToken();
            long id = insertQna("상태 변경 대상 " + suffix, "PENDING", false);

            mockMvc.perform(patch(BASE_URL + "/" + id + "/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"HIDDEN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.status").value("HIDDEN"));
        }

        @Test
        @DisplayName("AC-QNA-ADM-002-2: 존재하지 않는 id → 404")
        void changeStatus_notFound_returns404() throws Exception {
            givenAdminToken();

            mockMvc.perform(patch(BASE_URL + "/99999999/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"HIDDEN\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── AC-QNA-ADM-003 삭제 ───────────────────────────────────────────────

    @Nested
    @DisplayName("AC-QNA-ADM-003 삭제")
    class DeleteQna {

        @Test
        @DisplayName("AC-QNA-ADM-003: ADMIN DELETE → 204")
        void delete_asAdmin_returns204() throws Exception {
            givenAdminToken();
            long id = insertQna("삭제 대상 " + suffix, "PENDING", false);

            mockMvc.perform(delete(BASE_URL + "/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-QNA-ADM-003-2: 존재하지 않는 id → 404")
        void delete_notFound_returns404() throws Exception {
            givenAdminToken();

            mockMvc.perform(delete(BASE_URL + "/99999999")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── AC-QNA-ADM-004 권한 가드 ──────────────────────────────────────────

    @Nested
    @DisplayName("AC-QNA-ADM-004 권한 가드")
    class AuthGuard {

        @Test
        @DisplayName("AC-QNA-ADM-004: 미인증 요청 → 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-QNA-ADM-004-2: USER 역할 → 403")
        void userRole_returns403() throws Exception {
            givenUserToken(adminId, Set.of("USER"));

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── 헬퍼 ──────────────────────────────────────────────────────────────

    private void givenAdminToken() {
        givenUserToken(adminId, Set.of("ADMIN"));
    }

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "qna-admin-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', 'QNA관리자', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertQna(String title, String status, boolean isPrivate) {
        jdbcTemplate.update(
                "INSERT INTO qna (title, question_html, question_text, questioner_id, is_private, status) " +
                "VALUES (?, '<p>내용</p>', '내용', ?, ?, ?)",
                title, adminId, isPrivate, status);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM qna WHERE title = ? AND questioner_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, title, adminId);
        return id == null ? -1L : id;
    }
}
