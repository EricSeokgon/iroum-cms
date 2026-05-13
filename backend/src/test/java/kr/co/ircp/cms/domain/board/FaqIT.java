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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-003 Bundle B §G FAQ IT (REQ-BOARD-007-D-1 ~ D-5).
 *
 * <p>{@link kr.co.ircp.cms.domain.board.controller.FaqController} 의 카테고리·정렬·검색 +
 * 일괄 정렬 변경(reorder) 검증.
 *
 * <p>커버 AC: G-01(생성 ADMIN), G-02(목록), G-03(카테고리 카운트), G-04(reorder 200),
 * G-05(수정), G-06(삭제), G-07(USER POST → 403).
 *
 * <p>권한: FAQ 쓰기는 {@code hasAuthority('CONTENT:WRITE') or hasRole('ADMIN'|'SUPER_ADMIN'|'CONTENT_ADMIN')}.
 */
// @MX:NOTE: [AUTO] FaqIT — SPEC-CMS-003 §G 7 AC 통합 검증 (fan_in=0)
// @MX:SPEC: SPEC-CMS-003#REQ-BOARD-007
@AutoConfigureMockMvc
@DisplayName("FAQ IT (SPEC-CMS-003 §G)")
class FaqIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-faq-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long userId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("faq-admin-" + suffix);
        userId  = insertUser("faq-user-" + suffix);
    }

    // ─── §G-01 FAQ 생성 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("§G-01 FAQ 생성")
    class CreateFaq {

        @Test
        @DisplayName("G-01: ADMIN/CONTENT_ADMIN POST → 201 + id")
        void createFaq_asAdmin_returns201() throws Exception {
            givenAdminToken();
            String category = "cat_" + suffix;
            String body = """
                    {"categoryCode":"%s","question":"질문 본문 %s",
                     "answerHtml":"<p>답변 본문</p>","sortOrder":1}
                    """.formatted(category, suffix);

            mockMvc.perform(post("/api/v1/faqs")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.question").value("질문 본문 " + suffix));
        }

        @Test
        @DisplayName("G-07: USER 권한 FAQ 생성 시도 → 403")
        void createFaq_asUser_returns403() throws Exception {
            givenUserToken(userId, Set.of("USER"));
            String body = """
                    {"categoryCode":"u_%s","question":"USER 질문","answerHtml":"<p>x</p>","sortOrder":1}
                    """.formatted(suffix);

            mockMvc.perform(post("/api/v1/faqs")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── §G-02 / §G-03 조회 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("§G 조회")
    class ListFaqs {

        @Test
        @DisplayName("G-02: GET /faqs → 200 + content[] (공개 — 익명도 허용)")
        void listFaqs_returnsOk() throws Exception {
            insertFaq("list_" + suffix, "Q-1 " + suffix, 1, "PUBLISHED");
            insertFaq("list_" + suffix, "Q-2 " + suffix, 2, "PUBLISHED");

            givenAnonymousToken();
            mockMvc.perform(get("/api/v1/faqs")
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("G-03: GET /faqs/categories → 200 + List (공개)")
        void getCategories_returnsOk() throws Exception {
            insertFaq("catg_" + suffix, "Cat 질문", 1, "PUBLISHED");

            mockMvc.perform(get("/api/v1/faqs/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ─── §G-04 reorder ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("§G-04 reorder")
    class ReorderFaqs {

        @Test
        @DisplayName("G-04: ADMIN PUT /faqs/reorder → 204")
        void reorderFaqs_asAdmin_returnsNoContent() throws Exception {
            long id1 = insertFaqAndReturnId("reord_" + suffix, "Q-A", 1);
            long id2 = insertFaqAndReturnId("reord_" + suffix, "Q-B", 2);

            givenAdminToken();
            String body = """
                    {"items":[
                      {"id":%d,"sortOrder":10},
                      {"id":%d,"sortOrder":5}
                    ]}
                    """.formatted(id1, id2);

            mockMvc.perform(put("/api/v1/faqs/reorder")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            // DB 반영 확인
            Integer order1 = jdbcTemplate.queryForObject(
                    "SELECT sort_order FROM faq WHERE id = ?", Integer.class, id1);
            assert order1 != null && order1 == 10
                    : "id=" + id1 + " sort_order 가 10 이어야 함 (실제: " + order1 + ")";
        }
    }

    // ─── §G-05 / §G-06 수정·삭제 ─────────────────────────────────────────────

    @Nested
    @DisplayName("§G-05/06 수정·삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("G-05: ADMIN PUT /faqs/{id} → 200")
        void updateFaq_asAdmin_returns200() throws Exception {
            long id = insertFaqAndReturnId("upd_" + suffix, "수정전", 1);

            givenAdminToken();
            String body = """
                    {"categoryCode":"upd_%s","question":"수정후 질문",
                     "answerHtml":"<p>수정 답변</p>","sortOrder":2,"status":"PUBLISHED"}
                    """.formatted(suffix);

            mockMvc.perform(put("/api/v1/faqs/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.question").value("수정후 질문"));
        }

        @Test
        @DisplayName("G-06: ADMIN DELETE /faqs/{id} → 204")
        void deleteFaq_asAdmin_returns204() throws Exception {
            long id = insertFaqAndReturnId("del_" + suffix, "삭제대상", 1);

            givenAdminToken();
            mockMvc.perform(delete("/api/v1/faqs/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void givenAdminToken() {
        // FAQ 컨트롤러는 CONTENT:WRITE 권한 또는 ROLE_(ADMIN|SUPER_ADMIN|CONTENT_ADMIN) 요구
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "faq-admin-" + suffix,
                Set.of("ADMIN", "CONTENT_ADMIN"),
                Set.of("CONTENT:WRITE"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private void givenUserToken(long id, Set<String> roles) {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                id, "faq-user-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private void givenAnonymousToken() {
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', 'FAQ테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private void insertFaq(String categoryCode, String question, int sortOrder, String status) {
        jdbcTemplate.update(
                "INSERT INTO faq (category_code, question, answer_html, answer_text, " +
                "sort_order, status, created_by, created_at, updated_at) " +
                "VALUES (?, ?, '<p>답변</p>', '답변', ?, ?, ?, NOW(), NOW())",
                categoryCode, question, sortOrder, status, adminId);
    }

    private long insertFaqAndReturnId(String categoryCode, String question, int sortOrder) {
        insertFaq(categoryCode, question, sortOrder, "PUBLISHED");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM faq WHERE category_code = ? AND question = ? ORDER BY id DESC LIMIT 1",
                Long.class, categoryCode, question);
        return id == null ? -1L : id;
    }
}
