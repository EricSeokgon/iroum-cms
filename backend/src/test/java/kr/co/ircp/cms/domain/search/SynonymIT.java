package kr.co.ircp.cms.domain.search;

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

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-010 동의어 CRUD IT (REQ-SEARCH-009).
 *
 * <p>§E 동의어 사전 운영 CRUD 엔드포인트를 실제 PostgreSQL 16 환경에서 검증한다.
 * GET·POST·PUT·DELETE /api/v1/search/synonyms,
 * SELF 자기참조 제약, DUPLICATE 중복 제약, soft-delete, NOT_FOUND 처리를 커버한다.
 */
// @MX:NOTE: [AUTO] SynonymIT — SPEC-CMS-010 §E 동의어 CRUD IT (ADMIN 전용)
// @MX:SPEC: SPEC-CMS-010#REQ-SEARCH-009
@AutoConfigureMockMvc
@DisplayName("동의어 CRUD IT (SPEC-CMS-010 REQ-SEARCH-009)")
class SynonymIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-synonym-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long userId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("syn-admin-" + UUID.randomUUID().toString().substring(0, 8));
        userId  = insertUser("syn-user-"  + UUID.randomUUID().toString().substring(0, 8));
    }

    // ─── GET /api/v1/search/synonyms ─────────────────────────────────────────

    @Nested
    @DisplayName("§E-G: 동의어 목록 조회")
    class SynonymList {

        @Test
        @DisplayName("G-1: ADMIN — 200 OK + content 배열 반환")
        void list_asAdmin_returns200() throws Exception {
            insertSynonym("list-term-" + uid(), "list-syn-" + uid(), "ko", "ACTIVE");
            givenAdminToken();
            mockMvc.perform(get("/api/v1/search/synonyms")
                            .header("Authorization", TOKEN)
                            .param("locale", "ko"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("G-2: USER — 403 Forbidden")
        void list_asUser_returns403() throws Exception {
            givenUserToken();
            mockMvc.perform(get("/api/v1/search/synonyms")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── POST /api/v1/search/synonyms ────────────────────────────────────────

    @Nested
    @DisplayName("§E-C: 동의어 등록")
    class SynonymCreate {

        @Test
        @DisplayName("C-1: ADMIN — 정상 등록 201 + id 반환")
        void create_asAdmin_returns201() throws Exception {
            givenAdminToken();
            String term    = "cterm-" + uid();
            String synonym = "csyn-"  + uid();
            mockMvc.perform(post("/api/v1/search/synonyms")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"term\":\"" + term + "\",\"synonym\":\"" + synonym + "\",\"locale\":\"ko\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.term", is(term)))
                    .andExpect(jsonPath("$.synonym", is(synonym)))
                    .andExpect(jsonPath("$.status", is("ACTIVE")));
        }

        @Test
        @DisplayName("C-2: USER — 403 Forbidden")
        void create_asUser_returns403() throws Exception {
            givenUserToken();
            mockMvc.perform(post("/api/v1/search/synonyms")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"term\":\"테스트\",\"synonym\":\"test\",\"locale\":\"ko\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("C-3: term=synonym 자기참조 — 400 SEARCH_SYNONYM_SELF")
        void create_selfReference_returns400() throws Exception {
            givenAdminToken();
            String same = "selfterm-" + uid();
            mockMvc.perform(post("/api/v1/search/synonyms")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"term\":\"" + same + "\",\"synonym\":\"" + same + "\",\"locale\":\"ko\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("SEARCH_SYNONYM_SELF")));
        }

        @Test
        @DisplayName("C-4: UNIQUE(term,synonym,locale) 중복 — 409 SEARCH_SYNONYM_DUPLICATE")
        void create_duplicate_returns409() throws Exception {
            String term    = "dupterm-" + uid();
            String synonym = "dupsyn-"  + uid();
            insertSynonym(term, synonym, "ko", "ACTIVE");

            givenAdminToken();
            mockMvc.perform(post("/api/v1/search/synonyms")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"term\":\"" + term + "\",\"synonym\":\"" + synonym + "\",\"locale\":\"ko\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code", is("SEARCH_SYNONYM_DUPLICATE")));
        }
    }

    // ─── PUT /api/v1/search/synonyms/{id} ────────────────────────────────────

    @Nested
    @DisplayName("§E-U: 동의어 수정")
    class SynonymUpdate {

        @Test
        @DisplayName("U-1: ADMIN — synonym 수정 200 OK")
        void update_asAdmin_returns200() throws Exception {
            String term      = "uterm-" + uid();
            String oldSynonym = "uold-" + uid();
            long id = insertSynonymReturnId(term, oldSynonym, "ko", "ACTIVE");

            givenAdminToken();
            String newSynonym = "unew-" + uid();
            mockMvc.perform(put("/api/v1/search/synonyms/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"synonym\":\"" + newSynonym + "\",\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.synonym", is(newSynonym)));
        }

        @Test
        @DisplayName("U-2: 존재하지 않는 id — 404 SEARCH_SYNONYM_NOT_FOUND")
        void update_notFound_returns404() throws Exception {
            givenAdminToken();
            mockMvc.perform(put("/api/v1/search/synonyms/999999")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"synonym\":\"x\",\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── DELETE /api/v1/search/synonyms/{id} ─────────────────────────────────

    @Nested
    @DisplayName("§E-D: 동의어 삭제 (soft-delete)")
    class SynonymDelete {

        @Test
        @DisplayName("D-1: ADMIN — 204 No Content + status=PAUSED(soft-delete)")
        void delete_asAdmin_softDeletes() throws Exception {
            String term    = "dterm-" + uid();
            String synonym = "dsyn-"  + uid();
            long id = insertSynonymReturnId(term, synonym, "ko", "ACTIVE");

            givenAdminToken();
            mockMvc.perform(delete("/api/v1/search/synonyms/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            // soft-delete 확인 — DB 행은 남아 있고 status=PAUSED
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM search_synonym WHERE id = ?", String.class, id);
            assert "PAUSED".equals(status) : "soft-delete 후 status가 PAUSED여야 함, 실제=" + status;
        }

        @Test
        @DisplayName("D-2: 존재하지 않는 id — 404 SEARCH_SYNONYM_NOT_FOUND")
        void delete_notFound_returns404() throws Exception {
            givenAdminToken();
            mockMvc.perform(delete("/api/v1/search/synonyms/999998")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "syn-admin-" + adminId, Set.of("ADMIN"), Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private void givenUserToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "syn-user-" + userId, Set.of("USER"), Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '동의어테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private void insertSynonym(String term, String synonym, String locale, String status) {
        jdbcTemplate.update(
                "INSERT INTO search_synonym (term, synonym, locale, status, created_by, " +
                "created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON CONFLICT (term, synonym, locale) DO UPDATE SET status = EXCLUDED.status",
                term, synonym, locale, status, adminId);
    }

    private long insertSynonymReturnId(String term, String synonym, String locale, String status) {
        insertSynonym(term, synonym, locale, status);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM search_synonym WHERE term = ? AND synonym = ? AND locale = ?",
                Long.class, term, synonym, locale);
        return id == null ? -1L : id;
    }
}
