package kr.co.ircp.cms.domain.content.block;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-CONTENT-BLOCK-001 재사용 콘텐츠 블록 라이브러리 IT.
 *
 * <p>14개 AC(AC-001..AC-014)를 MockMvc 기반으로 검증한다.
 *
 * <p>인증 모델(PageIT 동일 관례):
 * <ul>
 *   <li>일반 관리자: CONTENT:READ, CONTENT:WRITE 권한 보유 CONTENT_ADMIN</li>
 *   <li>SUPER_ADMIN: HTML 블록 타입 생성 권한 추가 보유</li>
 * </ul>
 * {@code @WithMockUser} 대신 JWT 필터 경로(JwtTokenProvider Mock + Bearer 헤더)를 사용한다.
 */
// @MX:NOTE: [AUTO] ContentBlockIT — SPEC-CMS-CONTENT-BLOCK-001 14 AC 통합 검증
// @MX:SPEC: SPEC-CMS-CONTENT-BLOCK-001#REQ-CB-001~016
@AutoConfigureMockMvc
@DisplayName("공유 콘텐츠 블록 IT (SPEC-CMS-CONTENT-BLOCK-001)")
class ContentBlockIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-block-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("block-admin-" + uid());
    }

    // ─── AC-001: RICH_TEXT 생성 → 201 + DB 1행 + audit CREATE ───────────────────

    @Nested
    @DisplayName("AC-001: RICH_TEXT 블록 생성")
    class CreateRichText {

        @Test
        @DisplayName("AC-001: POST RICH_TEXT — 201 Created, DB 1행, audit_log action=CREATE")
        void createRichText_returns201_andPersists() throws Exception {
            givenWriteAdmin();
            String slug = "hero-" + uid();
            String body = """
                    {
                      "name": "히어로 배너",
                      "slug": "%s",
                      "blockType": "RICH_TEXT",
                      "contentHtml": "<p>환영합니다</p>",
                      "description": "메인 히어로"
                    }
                    """.formatted(slug);

            mockMvc.perform(post("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.slug").value(slug));

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared_content_block WHERE slug = ?", Integer.class, slug);
            org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);

            Integer audits = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'shared_content_block' " +
                            "AND action = 'CREATE'", Integer.class);
            org.assertj.core.api.Assertions.assertThat(audits).isGreaterThanOrEqualTo(1);
        }
    }

    // ─── AC-002: 중복 slug → 409 BLOCK_SLUG_DUPLICATE, DB insert 없음 ────────────

    @Nested
    @DisplayName("AC-002: 중복 slug")
    class DuplicateSlug {

        @Test
        @DisplayName("AC-002: 중복 slug — 409 Conflict, code=BLOCK_SLUG_DUPLICATE, insert 없음")
        void duplicateSlug_returns409() throws Exception {
            String slug = "dup-" + uid();
            insertBlock("기존", slug, "RICH_TEXT", "ACTIVE");

            givenWriteAdmin();
            String body = """
                    {"name":"신규","slug":"%s","blockType":"RICH_TEXT","contentHtml":"<p>x</p>"}
                    """.formatted(slug);

            mockMvc.perform(post("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BLOCK_SLUG_DUPLICATE"));

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared_content_block WHERE slug = ?", Integer.class, slug);
            org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);
        }
    }

    // ─── AC-003: ?status=ACTIVE 필터 ────────────────────────────────────────────

    @Nested
    @DisplayName("AC-003: status 필터")
    class StatusFilter {

        @Test
        @DisplayName("AC-003: 3 ACTIVE + 1 INACTIVE, ?status=ACTIVE — 200, 3개")
        void statusFilter_returnsOnlyActive() throws Exception {
            String tag = uid();
            insertBlock("a1", "act1-" + tag, "RICH_TEXT", "ACTIVE");
            insertBlock("a2", "act2-" + tag, "RICH_TEXT", "ACTIVE");
            insertBlock("a3", "act3-" + tag, "RICH_TEXT", "ACTIVE");
            insertBlock("i1", "inact-" + tag, "RICH_TEXT", "INACTIVE");

            givenReadAdmin();
            mockMvc.perform(get("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.status == 'INACTIVE')]").isEmpty());
        }
    }

    // ─── AC-004: HTML by non-SUPER_ADMIN → 403, insert 없음 ─────────────────────

    @Nested
    @DisplayName("AC-004: HTML 비-SUPER_ADMIN 차단")
    class HtmlForbidden {

        @Test
        @DisplayName("AC-004: HTML + non-SUPER_ADMIN — 403 Forbidden, insert 없음")
        void htmlByNonSuperAdmin_returns403() throws Exception {
            givenWriteAdmin(); // CONTENT_ADMIN, not SUPER_ADMIN
            String slug = "raw-" + uid();
            String body = """
                    {"name":"원본","slug":"%s","blockType":"HTML","contentHtml":"<script>x</script>"}
                    """.formatted(slug);

            mockMvc.perform(post("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared_content_block WHERE slug = ?", Integer.class, slug);
            org.assertj.core.api.Assertions.assertThat(rows).isZero();
        }
    }

    // ─── AC-005: PUT RICH_TEXT XSS → 200, script 제거 ───────────────────────────

    @Nested
    @DisplayName("AC-005: RICH_TEXT XSS 살균")
    class RichTextSanitize {

        @Test
        @DisplayName("AC-005: PUT RICH_TEXT XSS — 200, DB content_html 에서 script 제거")
        void updateRichTextXss_stripsScript() throws Exception {
            String slug = "xss-" + uid();
            long id = insertBlock("취약", slug, "RICH_TEXT", "ACTIVE");

            givenWriteAdmin();
            String body = """
                    {"name":"취약","slug":"%s","blockType":"RICH_TEXT",
                     "contentHtml":"<p>안전</p><script>alert('xss')</script>"}
                    """.formatted(slug);

            mockMvc.perform(put("/api/v1/content/blocks/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            String stored = jdbcTemplate.queryForObject(
                    "SELECT content_html FROM shared_content_block WHERE id = ?", String.class, id);
            org.assertj.core.api.Assertions.assertThat(stored).doesNotContain("<script");
        }
    }

    // ─── AC-006: DELETE → 204 + 삭제 + audit DELETE ─────────────────────────────

    @Nested
    @DisplayName("AC-006: 삭제")
    class DeleteBlock {

        @Test
        @DisplayName("AC-006: DELETE — 204 No Content, 행 삭제, audit_log action=DELETE")
        void delete_returns204_andAudits() throws Exception {
            String slug = "del-" + uid();
            long id = insertBlock("삭제대상", slug, "RICH_TEXT", "ACTIVE");

            givenWriteAdmin();
            mockMvc.perform(delete("/api/v1/content/blocks/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared_content_block WHERE id = ?", Integer.class, id);
            org.assertj.core.api.Assertions.assertThat(rows).isZero();

            Integer audits = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'shared_content_block' " +
                            "AND action = 'DELETE'", Integer.class);
            org.assertj.core.api.Assertions.assertThat(audits).isGreaterThanOrEqualTo(1);
        }
    }

    // ─── AC-007: PATCH /status → 200 + audit UPDATE ─────────────────────────────

    @Nested
    @DisplayName("AC-007: 상태 변경")
    class UpdateStatus {

        @Test
        @DisplayName("AC-007: PATCH /status — 200, status 갱신, audit_log action=UPDATE")
        void patchStatus_returns200_andAudits() throws Exception {
            String slug = "st-" + uid();
            long id = insertBlock("상태", slug, "RICH_TEXT", "ACTIVE");

            givenWriteAdmin();
            mockMvc.perform(patch("/api/v1/content/blocks/" + id + "/status")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"INACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));

            String stored = jdbcTemplate.queryForObject(
                    "SELECT status FROM shared_content_block WHERE id = ?", String.class, id);
            org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("INACTIVE");

            Integer audits = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'shared_content_block' " +
                            "AND action = 'UPDATE'", Integer.class);
            org.assertj.core.api.Assertions.assertThat(audits).isGreaterThanOrEqualTo(1);
        }
    }

    // ─── AC-008: 미존재 id → 404 BLOCK_NOT_FOUND ────────────────────────────────

    @Nested
    @DisplayName("AC-008: 미존재 조회")
    class NotFound {

        @Test
        @DisplayName("AC-008: GET /99999 — 404 Not Found, code=BLOCK_NOT_FOUND")
        void getNonexistent_returns404() throws Exception {
            givenReadAdmin();
            mockMvc.perform(get("/api/v1/content/blocks/99999")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("BLOCK_NOT_FOUND"));
        }
    }

    // ─── AC-009: 잘못된 slug 형식 → 400 ─────────────────────────────────────────

    @Nested
    @DisplayName("AC-009: slug 형식 위반")
    class InvalidSlug {

        @Test
        @DisplayName("AC-009: slug='Bad Slug!' — 400 Bad Request")
        void invalidSlug_returns400() throws Exception {
            givenWriteAdmin();
            String body = """
                    {"name":"잘못","slug":"Bad Slug!","blockType":"RICH_TEXT","contentHtml":"<p>x</p>"}
                    """;

            mockMvc.perform(post("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── AC-010: ?type=RICH_TEXT 필터 ───────────────────────────────────────────

    @Nested
    @DisplayName("AC-010: type 필터")
    class TypeFilter {

        @Test
        @DisplayName("AC-010: ?type=RICH_TEXT — 200, RICH_TEXT 만 반환")
        void typeFilter_returnsOnlyRichText() throws Exception {
            String tag = uid();
            insertBlock("r1", "rt-" + tag, "RICH_TEXT", "ACTIVE");
            insertBlock("m1", "md-" + tag, "MARKDOWN", "ACTIVE");

            givenReadAdmin();
            mockMvc.perform(get("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .param("type", "RICH_TEXT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.blockType == 'MARKDOWN')]").isEmpty());
        }
    }

    // ─── AC-011: GET /{id}/preview → 200 살균 HTML ──────────────────────────────

    @Nested
    @DisplayName("AC-011: 미리보기")
    class Preview {

        @Test
        @DisplayName("AC-011: GET /{id}/preview — 200, 살균 HTML 반환")
        void preview_returns200() throws Exception {
            String slug = "prev-" + uid();
            long id = insertBlock("미리보기", slug, "RICH_TEXT", "ACTIVE");

            givenReadAdmin();
            mockMvc.perform(get("/api/v1/content/blocks/" + id + "/preview")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.html").exists());
        }
    }

    // ─── AC-012: EMBED + tiktok.com → 422 BLOCK_EMBED_PROVIDER_INVALID ──────────

    @Nested
    @DisplayName("AC-012: EMBED provider 거부")
    class EmbedInvalid {

        @Test
        @DisplayName("AC-012: EMBED + tiktok.com — 422, code=BLOCK_EMBED_PROVIDER_INVALID")
        void embedInvalidProvider_returns422() throws Exception {
            givenWriteAdmin();
            String slug = "emb-" + uid();
            String body = """
                    {"name":"임베드","slug":"%s","blockType":"EMBED",
                     "contentRaw":"https://www.tiktok.com/@user/video/123"}
                    """.formatted(slug);

            mockMvc.perform(post("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("BLOCK_EMBED_PROVIDER_INVALID"));
        }
    }

    // ─── AC-013: PUT MARKDOWN XSS → 200, content_raw script 제거 ────────────────

    @Nested
    @DisplayName("AC-013: MARKDOWN XSS 살균")
    class MarkdownSanitize {

        @Test
        @DisplayName("AC-013: PUT MARKDOWN XSS — 200, DB content_raw 에서 script 제거")
        void updateMarkdownXss_stripsScript() throws Exception {
            String slug = "mdx-" + uid();
            long id = insertBlock("마크다운", slug, "MARKDOWN", "ACTIVE");

            givenWriteAdmin();
            String body = """
                    {"name":"마크다운","slug":"%s","blockType":"MARKDOWN",
                     "contentRaw":"# 제목 <script>alert('x')</script>"}
                    """.formatted(slug);

            mockMvc.perform(put("/api/v1/content/blocks/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            String stored = jdbcTemplate.queryForObject(
                    "SELECT content_raw FROM shared_content_block WHERE id = ?", String.class, id);
            org.assertj.core.api.Assertions.assertThat(stored).doesNotContain("<script");
        }
    }

    // ─── AC-014: EMBED + youtube.com → 201 ──────────────────────────────────────

    @Nested
    @DisplayName("AC-014: EMBED youtube 허용")
    class EmbedValid {

        @Test
        @DisplayName("AC-014: EMBED + youtube.com — 201 Created")
        void embedYoutube_returns201() throws Exception {
            givenWriteAdmin();
            String slug = "yt-" + uid();
            String body = """
                    {"name":"유튜브","slug":"%s","blockType":"EMBED",
                     "contentRaw":"https://www.youtube.com/watch?v=abc123"}
                    """.formatted(slug);

            mockMvc.perform(post("/api/v1/content/blocks")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.blockType").value("EMBED"));
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** CONTENT:READ + CONTENT:WRITE 권한 보유 일반 관리자 (SUPER_ADMIN 아님). */
    private void givenWriteAdmin() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "block-admin-" + adminId,
                Set.of("CONTENT_ADMIN"),
                Set.of("CONTENT:READ", "CONTENT:WRITE"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    /** CONTENT:READ 권한 보유 일반 관리자. */
    private void givenReadAdmin() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "block-admin-" + adminId,
                Set.of("CONTENT_ADMIN"),
                Set.of("CONTENT:READ"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '블록테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertBlock(String name, String slug, String blockType, String status) {
        jdbcTemplate.update(
                "INSERT INTO shared_content_block (name, slug, block_type, content_html, content_raw, " +
                        "status, created_by, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                name, slug, blockType,
                "RICH_TEXT".equals(blockType) || "HTML".equals(blockType) ? "<p>초기</p>" : null,
                "MARKDOWN".equals(blockType) ? "# 초기" : ("EMBED".equals(blockType) ? "https://www.youtube.com/watch?v=x" : null),
                status, adminId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM shared_content_block WHERE slug = ?", Long.class, slug);
        return id == null ? -1L : id;
    }
}
