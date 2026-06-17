package kr.co.ircp.cms.domain.point;

import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.service.PostService;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import kr.co.ircp.cms.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

/**
 * SPEC-CMS-POINTS-001 REQ-PNT-008 — 포인트 적립 best-effort 격리 IT (실DB).
 *
 * <p>{@link UserPointService}를 Mock으로 대체해 적립 시 예외를 던지도록 하고,
 * 게시글 작성(원인 행위)은 정상 완료(롤백되지 않음)되는지 검증한다.
 */
// @MX:NOTE: [AUTO] PostPointBestEffortIT — REQ-PNT-008 적립 예외가 게시글 작성을 차단하지 않음을 검증.
@DisplayName("게시글 포인트 best-effort IT (SPEC-CMS-POINTS-001 REQ-PNT-008)")
class PostPointBestEffortIT extends AbstractIntegrationTest {

    @Autowired PostService postService;
    @Autowired JdbcTemplate jdbcTemplate;

    // 적립 서비스를 Mock으로 대체 — 적립 예외가 게시글 작성을 롤백시키지 않음을 검증
    @MockitoBean UserPointService userPointService;

    private String suffix;
    private long userId;
    private long bbsId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userId = insertUser("be-user-" + suffix);
        bbsId = insertBoard("be_" + suffix);
    }

    @Test
    @DisplayName("REQ-PNT-008: 적립 예외 발생 시에도 게시글은 정상 저장된다")
    void createPost_completesNormally_whenAwardFails() {
        doThrow(new RuntimeException("적립 실패"))
                .when(userPointService).awardForPost(anyLong(), anyLong());

        PostDetail detail = postService.createPost(
                new PostCreateRequest(bbsId, "best-effort 제목", "<p>본문</p>", null,
                        false, null, null, false, null, null, null, List.of()),
                userId);

        assertThat(detail.id()).isNotNull();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bbs_post WHERE id = ?", Long.class, detail.id());
        assertThat(count).isEqualTo(1L);
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
}
