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
 * SPEC-CMS-REVIEW-001 Phase D2/D4 — {@link kr.co.ircp.cms.domain.board.controller.ReviewAdminController} IT.
 *
 * <p>관리자 리뷰 모더레이션 endpoint(목록 조회·숨김·삭제)와 권한 가드를 실 PostgreSQL 로 검증한다.
 *
 * <p>커버 AC:
 * <ul>
 *   <li>AC-REV-004: 인가 매트릭스 — 비인증 401 / 권한 부족 403 / REVIEW:READ 200</li>
 *   <li>AC-REV-006: 삭제 멱등성 — 두 호출 모두 204, DELETED 유지(복구 불가)</li>
 *   <li>AC-REV-007: 일반 사용자(REVIEW 권한 미보유) → 403 (GET/PATCH/DELETE 동일)</li>
 *   <li>AC-REV-010 부분: hide 후 VISIBLE 집계 제외 (실 DB 재집계)</li>
 * </ul>
 *
 * <p>인가 모델: {@code ReviewAdminController} 는 클래스 레벨 {@code @PreAuthorize("hasAuthority('REVIEW:READ')")},
 * hide/delete 는 메소드 레벨 {@code hasAuthority('REVIEW:DELETE')}. 따라서 권한은 역할(role)이 아닌
 * permission 어휘로 부여한다(AuthorizationMatrixExpandIT 패턴: JwtClaims.permissions).
 *
 * <p>hide/delete 는 운영 컨트롤러가 {@code ResponseEntity.noContent()} (204) 를 반환한다
 * — acceptance.md AC-REV-006 (삭제 204) 및 컨트롤러 구현과 일치.
 *
 * <p>board 패키지 배치: CommentAdminControllerIT 와 동일하게 security 패키지 endpoint baseline
 * (AuthorizationCoverageArchTest) 에 영향을 주지 않는 방어적 위치.
 */
// @MX:NOTE: [AUTO] ReviewAdminControllerIT — SPEC-CMS-REVIEW-001 관리자 모더레이션 + 인가 매트릭스 IT (fan_in=0)
// @MX:SPEC: SPEC-CMS-REVIEW-001#REQ-REV-004/006/007/010
@AutoConfigureMockMvc
@DisplayName("리뷰 관리자 모더레이션 IT (SPEC-CMS-REVIEW-001)")
class ReviewAdminControllerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-review-token";

    // REVIEW 권한 어휘 (V55 시드: REVIEW:READ / REVIEW:DELETE).
    private static final Set<String> READ_ONLY = Set.of("REVIEW:READ");
    private static final Set<String> READ_DELETE = Set.of("REVIEW:READ", "REVIEW:DELETE");
    private static final Set<String> NO_PERM = Set.of();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long userId;
    private long boardId;
    private long postId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userId = insertUser("rev-user-" + suffix);
        boardId = insertBbsMaster("rev-board-" + suffix, "리뷰 게시판 " + suffix);
        postId = insertPost(boardId, "리뷰 대상 게시글 " + suffix, userId);
    }

    // ─── AC-REV-004 목록 조회 인가 매트릭스 ───────────────────────────────────

    @Nested
    @DisplayName("AC-REV-004 목록 조회 인가")
    class ListReviews {

        @Test
        @DisplayName("AC-REV-004-A: REVIEW:READ 보유 GET /api/v1/admin/reviews → 200 + Page 구조")
        void listReviews_withReadPermission_returns200() throws Exception {
            insertReview(postId, userId, 4, "보이는 리뷰 " + suffix, "VISIBLE");
            insertReview(postId, userId, 2, "숨김 리뷰 " + suffix, "HIDDEN");

            givenToken(Set.of("USER"), READ_ONLY);
            mockMvc.perform(get("/api/v1/admin/reviews")
                            .header("Authorization", TOKEN)
                            .param("postId", String.valueOf(postId))
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").exists())
                    .andExpect(jsonPath("$.totalPages").exists());
        }

        @Test
        @DisplayName("AC-REV-004-B: 관리자 목록은 HIDDEN 리뷰도 포함")
        void listReviews_includesHidden() throws Exception {
            insertReview(postId, userId, 2, "숨김 전용 리뷰 " + suffix, "HIDDEN");

            givenToken(Set.of("USER"), READ_ONLY);
            mockMvc.perform(get("/api/v1/admin/reviews")
                            .header("Authorization", TOKEN)
                            .param("postId", String.valueOf(postId))
                            .param("status", "HIDDEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("HIDDEN"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("AC-REV-007-A: REVIEW 권한 미보유(USER) GET → 403")
        void listReviews_withoutPermission_returns403() throws Exception {
            givenToken(Set.of("USER"), NO_PERM);
            mockMvc.perform(get("/api/v1/admin/reviews")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-REV-004-C: 비인증 GET → 401")
        void listReviews_withNoAuth_returns401() throws Exception {
            int code = mockMvc.perform(get("/api/v1/admin/reviews"))
                    .andReturn().getResponse().getStatus();
            assert code == 401 : "비인증 admin 리뷰 GET 응답은 401 (실제: " + code + ")";
        }
    }

    // ─── 리뷰 숨김(hide) ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-REV-010 리뷰 숨김")
    class HideReview {

        @Test
        @DisplayName("AC-REV-010-A: REVIEW:DELETE 보유 PATCH /hide → 204 + DB status=HIDDEN")
        void hideReview_withDeletePermission_returns204() throws Exception {
            long reviewId = insertReview(postId, userId, 5, "숨김 대상 리뷰 " + suffix, "VISIBLE");

            givenToken(Set.of("ADMIN"), READ_DELETE);
            mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/hide")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM bbs_post_review WHERE id = ?", String.class, reviewId);
            assert "HIDDEN".equals(dbStatus) : "리뷰 status 는 HIDDEN 이어야 함 (실제: " + dbStatus + ")";
        }

        @Test
        @DisplayName("AC-REV-010-B: hide 후 게시물 VISIBLE 집계에서 제외 (review_count 감소)")
        void hideReview_excludesFromAggregate() throws Exception {
            long keepId = insertReview(postId, userId, 4, "유지 리뷰 " + suffix, "VISIBLE");
            long hideId = insertReview(postId, userId, 2, "숨김 리뷰 " + suffix, "VISIBLE");
            // 초기 집계 세팅 (VISIBLE 2건)
            jdbcTemplate.update("UPDATE bbs_post SET review_count = 2, average_rating = 3.0 WHERE id = ?", postId);

            givenToken(Set.of("ADMIN"), READ_DELETE);
            mockMvc.perform(patch("/api/v1/admin/reviews/" + hideId + "/hide")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT review_count FROM bbs_post WHERE id = ?", Integer.class, postId);
            assert count != null && count == 1 : "hide 후 VISIBLE 집계는 1건이어야 함 (실제: " + count + ")";
            // keepId 는 VISIBLE 유지
            String keepStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM bbs_post_review WHERE id = ?", String.class, keepId);
            assert "VISIBLE".equals(keepStatus) : "유지 리뷰는 VISIBLE 이어야 함";
        }

        @Test
        @DisplayName("AC-REV-007-B: REVIEW:DELETE 미보유(READ만) PATCH /hide → 403")
        void hideReview_withoutDeletePermission_returns403() throws Exception {
            long reviewId = insertReview(postId, userId, 3, "권한부족 숨김 " + suffix, "VISIBLE");

            givenToken(Set.of("USER"), READ_ONLY); // REVIEW:DELETE 미보유
            mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/hide")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── AC-REV-006 리뷰 삭제 + 멱등성 ────────────────────────────────────────

    @Nested
    @DisplayName("AC-REV-006 리뷰 삭제 멱등성")
    class DeleteReview {

        @Test
        @DisplayName("AC-REV-006-A: REVIEW:DELETE 보유 DELETE → 204 + DB status=DELETED")
        void deleteReview_withDeletePermission_returns204() throws Exception {
            long reviewId = insertReview(postId, userId, 4, "삭제 대상 리뷰 " + suffix, "VISIBLE");

            givenToken(Set.of("ADMIN"), READ_DELETE);
            mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM bbs_post_review WHERE id = ?", String.class, reviewId);
            assert "DELETED".equals(dbStatus) : "리뷰 status 는 DELETED 이어야 함 (실제: " + dbStatus + ")";
            Integer deletedAtCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bbs_post_review WHERE id = ? AND deleted_at IS NOT NULL",
                    Integer.class, reviewId);
            assert deletedAtCount != null && deletedAtCount == 1 : "deleted_at 이 기록되어야 함";
        }

        @Test
        @DisplayName("AC-REV-006-B: 동일 리뷰 2회 삭제 → 두 호출 모두 204 (멱등) + DELETED 유지")
        void deleteReview_idempotent_secondCallAlso204() throws Exception {
            long reviewId = insertReview(postId, userId, 5, "멱등 삭제 리뷰 " + suffix, "VISIBLE");

            givenToken(Set.of("ADMIN"), READ_DELETE);

            // 1차 삭제 → 204
            mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            // 2차 삭제(이미 DELETED) → 204 멱등
            mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            // DELETED 상태 유지 — 복구되지 않음
            String dbStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM bbs_post_review WHERE id = ?", String.class, reviewId);
            assert "DELETED".equals(dbStatus) : "재삭제 후에도 DELETED 유지여야 함 (실제: " + dbStatus + ")";
        }

        @Test
        @DisplayName("AC-REV-007-C: REVIEW:DELETE 미보유(READ만) DELETE → 403")
        void deleteReview_withoutDeletePermission_returns403() throws Exception {
            long reviewId = insertReview(postId, userId, 3, "권한부족 삭제 " + suffix, "VISIBLE");

            givenToken(Set.of("USER"), READ_ONLY); // REVIEW:DELETE 미보유
            mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AC-REV-004-D: 비인증 DELETE → 401")
        void deleteReview_withNoAuth_returns401() throws Exception {
            long reviewId = insertReview(postId, userId, 3, "비인증 삭제 " + suffix, "VISIBLE");

            int code = mockMvc.perform(delete("/api/v1/admin/reviews/" + reviewId))
                    .andReturn().getResponse().getStatus();
            assert code == 401 : "비인증 admin 리뷰 DELETE 응답은 401 (실제: " + code + ")";
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    /**
     * 지정 roles/permissions 로 valid 토큰을 시뮬레이션한다.
     * permissions 가 운영에서 그대로 authority 로 사용되므로 REVIEW:READ/DELETE 권한 시나리오를 표현.
     */
    private void givenToken(Set<String> roles, Set<String> permissions) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "rev-" + userId, roles, permissions,
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '리뷰테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
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

    private long insertReview(long pId, long authorId, int rating, String content, String status) {
        // deleted_at 은 status=DELETED 일 때만 기록 (운영 강제삭제 거동 모사).
        if ("DELETED".equals(status)) {
            jdbcTemplate.update(
                    "INSERT INTO bbs_post_review (post_id, author_id, rating, content, status, " +
                    "created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW(), NOW())",
                    pId, authorId, rating, content, status);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO bbs_post_review (post_id, author_id, rating, content, status, " +
                    "created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                    pId, authorId, rating, content, status);
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_post_review WHERE content = ? ORDER BY id DESC LIMIT 1",
                Long.class, content);
        return id == null ? -1L : id;
    }
}
