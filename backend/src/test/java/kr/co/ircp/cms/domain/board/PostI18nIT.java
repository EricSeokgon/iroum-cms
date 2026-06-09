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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-NOTICE-I18N-002 통합 테스트.
 *
 * <p>검증 AC:
 * AC-NI2-001: lang 파라미터가 서비스까지 전달됨.
 * AC-NI2-002: lang=en 요청 시 번역이 있는 항목은 EN 제목, language='en'.
 * AC-NI2-003: 번역이 없는 항목은 원본 제목, language='ko'.
 */
// @MX:NOTE: [AUTO] PostI18nIT — SPEC-CMS-NOTICE-I18N-002 목록 번역 오버레이 검증
// @MX:SPEC: SPEC-CMS-NOTICE-I18N-002
@AutoConfigureMockMvc
@DisplayName("게시글 목록 i18n 오버레이 IT (SPEC-CMS-NOTICE-I18N-002)")
class PostI18nIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-i18n-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long authorId;
    private long bbsId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        authorId = insertUser("i18n-user-" + suffix);
        bbsId = insertBoard("i18n_" + suffix);
        givenUserToken(authorId, Set.of("USER"));
    }

    // ─── AC-NI2-001 ~ AC-NI2-003 ─────────────────────────────────────────────

    @Nested
    @DisplayName("AC-NI2: lang=en 번역 오버레이")
    class LangEnOverlay {

        @Test
        @DisplayName("AC-NI2-002: 번역이 있는 게시글 → EN 제목, language='en'")
        void langEn_withTranslation_returnsEnTitle() throws Exception {
            long postId = insertPost(bbsId, "원본 제목", authorId);
            insertI18n(postId, "en", "English Title");

            mockMvc.perform(get("/api/v1/board/posts")
                            .header("Authorization", TOKEN)
                            .param("bbsId", String.valueOf(bbsId))
                            .param("lang", "en"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title", is("English Title")))
                    .andExpect(jsonPath("$.content[0].language", is("en")));
        }

        @Test
        @DisplayName("AC-NI2-003: 번역이 없는 게시글 → 원본 제목, language='ko'")
        void langEn_withoutTranslation_returnsKoTitle() throws Exception {
            insertPost(bbsId, "원본만 있는 글", authorId);

            mockMvc.perform(get("/api/v1/board/posts")
                            .header("Authorization", TOKEN)
                            .param("bbsId", String.valueOf(bbsId))
                            .param("lang", "en"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title", is("원본만 있는 글")))
                    .andExpect(jsonPath("$.content[0].language", is("ko")));
        }

        @Test
        @DisplayName("AC-NI2-TEST-1: 혼합 페이지 — 번역 있는 글과 없는 글 각각 language 분리")
        void langEn_mixedPage_correctLanguagePerItem() throws Exception {
            long postWithTrans = insertPost(bbsId, "번역 있는 글", authorId);
            insertPost(bbsId, "번역 없는 글", authorId);
            insertI18n(postWithTrans, "en", "Post With Translation");

            // id DESC 정렬: 나중에 삽입된 '번역 없는 글'이 [0], '번역 있는 글'이 [1]
            mockMvc.perform(get("/api/v1/board/posts")
                            .header("Authorization", TOKEN)
                            .param("bbsId", String.valueOf(bbsId))
                            .param("lang", "en"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(2)))
                    .andExpect(jsonPath("$.content[0].language", is("ko")))
                    .andExpect(jsonPath("$.content[0].title", is("번역 없는 글")))
                    .andExpect(jsonPath("$.content[1].language", is("en")))
                    .andExpect(jsonPath("$.content[1].title", is("Post With Translation")));
        }

        @Test
        @DisplayName("AC-NI2-TEST-2: 회귀 — lang=ko 요청 시 원본 제목, language='ko'")
        void langKo_alwaysReturnsKo() throws Exception {
            long postId = insertPost(bbsId, "한국어 원본", authorId);
            insertI18n(postId, "en", "English Title");

            mockMvc.perform(get("/api/v1/board/posts")
                            .header("Authorization", TOKEN)
                            .param("bbsId", String.valueOf(bbsId))
                            .param("lang", "ko"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title", is("한국어 원본")))
                    .andExpect(jsonPath("$.content[0].language", is("ko")));
        }
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "i18n-user-" + suffix, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'hash', 'i18n테스터', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertBoard(String code) {
        jdbcTemplate.update(
                "INSERT INTO bbs_master (code, name, type, use_comment, use_attachment, " +
                "max_attachment_count, max_attachment_size_kb, page_size, status) " +
                "VALUES (?, ?, 'NORMAL', false, false, 5, 10240, 20, 'ACTIVE')",
                code, code + "-name");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM bbs_master WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private long insertPost(long bbsId, String title, long authorId) {
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

    private void insertI18n(long postId, String language, String title) {
        jdbcTemplate.update(
                "INSERT INTO bbs_post_i18n (post_id, language, title, updated_at) " +
                "VALUES (?, ?, ?, NOW())",
                postId, language, title);
    }
}
