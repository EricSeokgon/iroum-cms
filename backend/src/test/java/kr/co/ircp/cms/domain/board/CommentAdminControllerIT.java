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
 * SPEC-CMS-COMMENT-MODERATE-001 댓글 관리자 모더레이션 IT.
 *
 * <p>{@link kr.co.ircp.cms.domain.board.controller.CommentAdminController} 의
 * 전체 댓글 목록 조회·필터링·상태 변경·강제 삭제 + 권한 가드를 검증한다.
 *
 * <p>커버 AC: AC-CMTM-001(목록 Page), AC-CMTM-002(status/keyword/boardId 필터),
 * AC-CMTM-003(상태 변경 + DELETED→VISIBLE 400), AC-CMTM-004(소프트 삭제 204 + idempotent),
 * AC-CMTM-005(401 비인증 / 403 USER).
 *
 * <p>인가: 클래스 레벨 @PreAuthorize("hasAnyRole('ADMIN','MANAGER')").
 * 본 IT 는 board 패키지에 위치하여 AuthorizationCoverageArchTest(security 패키지 스캔)의
 * endpoint baseline 에 영향을 주지 않는다(방어적 배치).
 */
// @MX:NOTE: [AUTO] CommentAdminControllerIT — SPEC-CMS-COMMENT-MODERATE-001 5 AC 통합 검증 (fan_in=0)
// @MX:SPEC: SPEC-CMS-COMMENT-MODERATE-001#REQ-CMTM-001~005
@AutoConfigureMockMvc
@DisplayName("댓글 관리자 모더레이션 IT (SPEC-CMS-COMMENT-MODERATE-001)")
class CommentAdminControllerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-cmtm-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long userId;
    private long boardId;
    private long otherBoardId;
    private long postId;
    private long otherPostId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("cmtm-admin-" + suffix);
        userId = insertUser("cmtm-user-" + suffix);
        boardId = insertBbsMaster("cmtm-board-" + suffix, "모더레이션 게시판 " + suffix);
        otherBoardId = insertBbsMaster("cmtm-other-" + suffix, "다른 게시판 " + suffix);
        postId = insertPost(boardId, "모더레이션 대상 게시글 " + suffix, adminId);
        otherPostId = insertPost(otherBoardId, "다른 게시판 게시글 " + suffix, adminId);
    }

    // ─── AC-CMTM-001 전체 목록 조회 ───────────────────────────────────────────

    @Nested
    @DisplayName("AC-CMTM-001 전체 댓글 목록")
    class ListComments {

        @Test
        @DisplayName("AC-CMTM-001: ADMIN GET /api/v1/admin/comments → 200 + Page 구조")
        void listComments_asAdmin_returnsPage() throws Exception {
            insertComment(postId, userId, "첫 번째 댓글 " + suffix, "VISIBLE");
            insertComment(postId, userId, "두 번째 댓글 " + suffix, "VISIBLE");

            givenAdminToken();
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN)
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").exists())
                    .andExpect(jsonPath("$.totalPages").exists());
        }

        @Test
        @DisplayName("AC-CMTM-001-2: 응답 항목에 postTitle/boardName/contentPreview 포함")
        void listComments_includesSummaryFields() throws Exception {
            insertComment(postId, userId, "요약 필드 검증 댓글 " + suffix, "VISIBLE");

            givenAdminToken();
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN)
                            .param("keyword", "요약 필드 검증 댓글 " + suffix))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].postTitle").value("모더레이션 대상 게시글 " + suffix))
                    .andExpect(jsonPath("$.content[0].boardName").value("모더레이션 게시판 " + suffix))
                    .andExpect(jsonPath("$.content[0].authorUsername").value("cmtm-user-" + suffix))
                    .andExpect(jsonPath("$.content[0].contentPreview").exists());
        }
    }

    // ─── AC-CMTM-002 필터링 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-CMTM-002 필터")
    class FilterComments {

        @Test
        @DisplayName("AC-CMTM-002-A: ?status=HIDDEN → HIDDEN 댓글만 반환")
        void filterByStatus_hidden() throws Exception {
            insertComment(postId, userId, "보이는 댓글 " + suffix, "VISIBLE");
            insertComment(postId, userId, "숨김 댓글 " + suffix, "HIDDEN");

            givenAdminToken();
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN)
                            .param("status", "HIDDEN")
                            .param("keyword", suffix))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("HIDDEN"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("AC-CMTM-002-B: ?keyword=고유어 → content 부분일치만 반환")
        void filterByKeyword() throws Exception {
            String marker = "유니크키워드" + suffix;
            insertComment(postId, userId, "앞부분 " + marker + " 뒷부분", "VISIBLE");
            insertComment(postId, userId, "관련 없는 댓글 " + suffix, "VISIBLE");

            givenAdminToken();
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN)
                            .param("keyword", marker))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("AC-CMTM-002-C: ?boardId=X → 해당 게시판 댓글만 반환")
        void filterByBoardId() throws Exception {
            insertComment(postId, userId, "대상 게시판 댓글 " + suffix, "VISIBLE");
            insertComment(otherPostId, userId, "다른 게시판 댓글 " + suffix, "VISIBLE");

            givenAdminToken();
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN)
                            .param("boardId", String.valueOf(boardId))
                            .param("keyword", suffix))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].boardName").value("모더레이션 게시판 " + suffix));
        }
    }

    // ─── AC-CMTM-003 상태 변경 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-CMTM-003 상태 변경")
    class ChangeStatus {

        @Test
        @DisplayName("AC-CMTM-003-A: PATCH status=HIDDEN → 200 + DB status=HIDDEN")
        void changeStatus_toHidden_returns200() throws Exception {
            long commentId = insertComment(postId, userId, "상태변경 대상 " + suffix, "VISIBLE");

            givenAdminToken();
            mockMvc.perform(patch("/api/v1/admin/comments/" + commentId + "/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"HIDDEN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("HIDDEN"));

            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM bbs_comment WHERE id = ?", String.class, commentId);
            assert "HIDDEN".equals(dbStatus) : "댓글 status 는 HIDDEN 이어야 함 (실제: " + dbStatus + ")";
        }

        @Test
        @DisplayName("AC-CMTM-003-B: DELETED 댓글에 VISIBLE 변경 → 400")
        void changeStatus_deletedToVisible_returns400() throws Exception {
            long commentId = insertComment(postId, userId, "삭제된 댓글 " + suffix, "DELETED");

            givenAdminToken();
            mockMvc.perform(patch("/api/v1/admin/comments/" + commentId + "/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"VISIBLE\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── AC-CMTM-004 강제 삭제 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-CMTM-004 강제 삭제")
    class DeleteComment {

        @Test
        @DisplayName("AC-CMTM-004-A: DELETE → 204 + DB status=DELETED + deleted_at 기록")
        void deleteComment_returns204() throws Exception {
            long commentId = insertComment(postId, userId, "삭제 대상 " + suffix, "VISIBLE");

            givenAdminToken();
            mockMvc.perform(delete("/api/v1/admin/comments/" + commentId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM bbs_comment WHERE id = ?", String.class, commentId);
            assert "DELETED".equals(dbStatus) : "댓글 status 는 DELETED 이어야 함 (실제: " + dbStatus + ")";
            Integer deletedAtCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bbs_comment WHERE id = ? AND deleted_at IS NOT NULL",
                    Integer.class, commentId);
            assert deletedAtCount != null && deletedAtCount == 1 : "deleted_at 이 기록되어야 함";
        }

        @Test
        @DisplayName("AC-CMTM-004-B: 이미 DELETED 인 댓글 재삭제 → 204 (idempotent)")
        void deleteComment_idempotent() throws Exception {
            long commentId = insertComment(postId, userId, "재삭제 대상 " + suffix, "DELETED");

            givenAdminToken();
            mockMvc.perform(delete("/api/v1/admin/comments/" + commentId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }
    }

    // ─── AC-CMTM-005 권한 가드 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-CMTM-005 권한")
    class Authorization {

        @Test
        @DisplayName("AC-CMTM-005-A: 비인증 GET → 401")
        void unauthenticated_returns401() throws Exception {
            int code = mockMvc.perform(get("/api/v1/admin/comments"))
                    .andReturn().getResponse().getStatus();
            assert code == 401 : "비인증 admin 댓글 GET 응답은 401 (실제: " + code + ")";
        }

        @Test
        @DisplayName("AC-CMTM-005-B: USER 권한 GET → 403")
        void userRole_returns403() throws Exception {
            givenUserToken(userId, Set.of("USER"));
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-CMTM-005-C: MANAGER 권한 GET → 200")
        void managerRole_returns200() throws Exception {
            givenUserToken(adminId, Set.of("MANAGER"));
            mockMvc.perform(get("/api/v1/admin/comments")
                            .header("Authorization", TOKEN)
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void givenAdminToken() {
        givenUserToken(adminId, Set.of("ADMIN"));
    }

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "cmtm-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '모더레이션테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertBbsMaster(String code, String name) {
        jdbcTemplate.update(
                "INSERT INTO bbs_master (code, name, type, created_at, updated_at) " +
                "VALUES (?, ?, 'NORMAL', NOW(), NOW())",
                code, name);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_master WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private long insertPost(long bbsId, String title, long authorId) {
        jdbcTemplate.update(
                "INSERT INTO bbs_post (bbs_id, title, content_html, content_text, author_id, " +
                "status, created_at, updated_at) " +
                "VALUES (?, ?, '<p>본문</p>', '본문', ?, 'PUBLISHED', NOW(), NOW())",
                bbsId, title, authorId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_post WHERE title = ? ORDER BY id DESC LIMIT 1",
                Long.class, title);
        return id == null ? -1L : id;
    }

    private long insertComment(long pId, long authorId, String content, String status) {
        // deleted_at 은 status=DELETED 일 때만 기록 (운영 강제삭제 거동 모사)
        if ("DELETED".equals(status)) {
            jdbcTemplate.update(
                    "INSERT INTO bbs_comment (post_id, author_id, content, status, " +
                    "created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, NOW(), NOW(), NOW())",
                    pId, authorId, content, status);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO bbs_comment (post_id, author_id, content, status, " +
                    "created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
                    pId, authorId, content, status);
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_comment WHERE content = ? ORDER BY id DESC LIMIT 1",
                Long.class, content);
        return id == null ? -1L : id;
    }
}
