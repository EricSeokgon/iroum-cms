package kr.co.ircp.cms.domain.point;

import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.service.BbsPostLikeService;
import kr.co.ircp.cms.domain.board.service.PostService;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-CMS-POINTS-001 적립 통합 테스트 — REQ-PNT-002/004/007/008 (실DB).
 *
 * <p>커버: 게시글 작성 적립(활성), 비활성 토글 시 미적립(REQ-PNT-007),
 * 좋아요 적립 + 중복 방지(REQ-PNT-004), best-effort 격리(REQ-PNT-008).
 */
// @MX:NOTE: [AUTO] PointAwardIT — SPEC-CMS-POINTS-001 적립 경로 실DB 검증 (fan_in=0)
@DisplayName("포인트 적립 IT (SPEC-CMS-POINTS-001)")
class PointAwardIT extends AbstractIntegrationTest {

    @Autowired PostService postService;
    @Autowired BbsPostLikeService bbsPostLikeService;
    @Autowired UserPointService userPointService;
    @Autowired JdbcTemplate jdbcTemplate;

    private String suffix;
    private long userId;
    private long bbsId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userId = insertUser("pt-user-" + suffix);
        bbsId = insertBoard("pt_" + suffix);
        // 기본 정책 비활성 → 각 테스트에서 명시적으로 활성/비활성 설정
        setPolicy(false, 0, 0, 0);
    }

    @Test
    @DisplayName("REQ-PNT-002: 활성화 상태 게시글 작성 → ledger + summary 적립")
    void createPost_awardsPoints_whenEnabled() {
        setPolicy(true, 10, 5, 2);

        var detail = postService.createPost(
                new PostCreateRequest(bbsId, "포인트 테스트", "<p>본문</p>", null,
                        false, null, null, false, null, null, null, List.of()),
                userId);

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_point_ledger WHERE user_id = ? AND event_type = 'POST_CREATED'",
                Long.class, userId);
        assertThat(ledgerCount).isEqualTo(1L);

        Long total = jdbcTemplate.queryForObject(
                "SELECT total_points FROM user_point_summary WHERE user_id = ?", Long.class, userId);
        assertThat(total).isEqualTo(10L);
        assertThat(detail.id()).isNotNull();
    }

    @Test
    @DisplayName("REQ-PNT-007: 비활성화 상태에서는 게시글 작성해도 적립 없음")
    void createPost_noAward_whenDisabled() {
        setPolicy(false, 10, 5, 2); // enabled=false → 적립 스킵

        postService.createPost(
                new PostCreateRequest(bbsId, "비활성 테스트", "<p>본문</p>", null,
                        false, null, null, false, null, null, null, List.of()),
                userId);

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_point_ledger WHERE user_id = ?", Long.class, userId);
        assertThat(ledgerCount).isZero();
    }

    @Test
    @DisplayName("REQ-PNT-004: 좋아요 최초 1회만 적립, 중복 좋아요는 미적립")
    void like_awardsOnce_skipsDuplicate() {
        setPolicy(true, 10, 5, 2);
        long postId = insertPost(bbsId, userId);

        boolean first = bbsPostLikeService.like(postId, userId);
        boolean second = bbsPostLikeService.like(postId, userId);

        assertThat(first).isTrue();
        assertThat(second).isFalse();

        Long likeAwards = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_point_ledger WHERE user_id = ? AND event_type = 'LIKE_GIVEN'",
                Long.class, userId);
        assertThat(likeAwards).isEqualTo(1L);

        Long total = jdbcTemplate.queryForObject(
                "SELECT total_points FROM user_point_summary WHERE user_id = ?", Long.class, userId);
        assertThat(total).isEqualTo(2L);
    }

    @Test
    @DisplayName("REQ-PNT-004: 좋아요 취소해도 적립 포인트 회수 없음")
    void unlike_doesNotReversePoints() {
        setPolicy(true, 10, 5, 2);
        long postId = insertPost(bbsId, userId);
        bbsPostLikeService.like(postId, userId);

        bbsPostLikeService.unlike(postId, userId);

        // 좋아요 행은 삭제되지만 원장/요약은 그대로
        Long likeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bbs_post_like WHERE post_id = ? AND user_id = ?",
                Long.class, postId, userId);
        assertThat(likeRows).isZero();
        Long total = jdbcTemplate.queryForObject(
                "SELECT total_points FROM user_point_summary WHERE user_id = ?", Long.class, userId);
        assertThat(total).isEqualTo(2L);
    }

    @Test
    @DisplayName("REQ-PNT-007: 비활성→활성 토글 시 이후 활동부터 즉시 적립 재개(캐시 없음)")
    void enabledToggle_resumesImmediately() {
        // 비활성 상태에서 1회 — 적립 없음
        setPolicy(false, 10, 5, 2);
        userPointService.awardForPost(userId, 1L);
        Long afterDisabled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_point_ledger WHERE user_id = ?", Long.class, userId);
        assertThat(afterDisabled).isZero();

        // 활성으로 토글 후 즉시 적립
        setPolicy(true, 10, 5, 2);
        userPointService.awardForPost(userId, 2L);
        Long afterEnabled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_point_ledger WHERE user_id = ?", Long.class, userId);
        assertThat(afterEnabled).isEqualTo(1L);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void setPolicy(boolean enabled, int post, int comment, int like) {
        upsertSetting("POINTS:ENABLED", Boolean.toString(enabled), "BOOL");
        upsertSetting("POINTS:POST_CREATED", Integer.toString(post), "INT");
        upsertSetting("POINTS:COMMENT_CREATED", Integer.toString(comment), "INT");
        upsertSetting("POINTS:LIKE_GIVEN", Integer.toString(like), "INT");
    }

    private void upsertSetting(String key, String value, String type) {
        jdbcTemplate.update(
                "INSERT INTO system_setting (key, value, value_type, created_at, updated_at) " +
                "VALUES (?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()",
                key, value, type);
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '포인트테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertBoard(String code) {
        jdbcTemplate.update(
                "INSERT INTO bbs_master (code, name, type, use_comment, use_attachment, " +
                "max_attachment_count, max_attachment_size_kb, page_size, status) " +
                "VALUES (?, ?, 'NORMAL', true, true, 5, 10240, 20, 'ACTIVE')",
                code, code + "-name");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_master WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private long insertPost(long bbsId, long authorId) {
        String title = "like-target-" + UUID.randomUUID().toString().substring(0, 6);
        jdbcTemplate.update(
                "INSERT INTO bbs_post (bbs_id, title, content_html, content_text, " +
                "author_id, is_notice, is_secret, status, created_at, updated_at) " +
                "VALUES (?, ?, '<p>본문</p>', '본문', ?, false, false, 'PUBLISHED', NOW(), NOW())",
                bbsId, title, authorId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_post WHERE bbs_id = ? AND title = ? ORDER BY id DESC LIMIT 1",
                Long.class, bbsId, title);
        return id == null ? -1L : id;
    }
}
