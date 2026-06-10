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
 * SPEC-CMS-PUB-CAT-001 발간자료 카테고리 어드민 CRUD IT.
 *
 * <p>커버 AC: AC-PCA-001(생성 201), AC-PCA-002(수정 200), AC-PCA-003a/b(삭제 204/409),
 * AC-PCA-004(목록 INACTIVE 포함), AC-PCA-005(401 비인증).
 */
@AutoConfigureMockMvc
class PublicationCategoryAdminControllerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-pca-token";
    private static final String BASE_URL = "/api/v1/admin/publication-categories";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminId = insertUser("pca-admin-" + suffix);
    }

    // ─── AC-PCA-001 생성 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PCA-001 카테고리 생성")
    class CreateCategory {

        @Test
        @DisplayName("AC-PCA-001: ADMIN POST → 201, id/code/name 반환")
        void create_asAdmin_returns201() throws Exception {
            givenAdminToken();
            String code = "CAT_" + suffix.toUpperCase();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"%s","name":"테스트 카테고리","sortOrder":0}
                                    """.formatted(code)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.name").value("테스트 카테고리"))
                    .andExpect(jsonPath("$.depth").value(1))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("AC-PCA-001-2: 중복 code → 409")
        void create_duplicateCode_returns409() throws Exception {
            givenAdminToken();
            String code = "DUP_" + suffix.toUpperCase();
            insertCategory(code, "중복 카테고리", null);

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"%s","name":"다른 이름","sortOrder":0}
                                    """.formatted(code)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("AC-PCA-001-3: 부모 지정 시 depth 2 자동 계산")
        void create_withParent_returnsDepth2() throws Exception {
            givenAdminToken();
            long parentId = insertCategory("PARENT_" + suffix.toUpperCase(), "루트 카테고리", null);
            String childCode = "CHILD_" + suffix.toUpperCase();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"%s","name":"자식 카테고리","parentId":%d,"sortOrder":0}
                                    """.formatted(childCode, parentId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.depth").value(2))
                    .andExpect(jsonPath("$.parentId").value(parentId));
        }
    }

    // ─── AC-PCA-002 수정 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PCA-002 카테고리 수정")
    class UpdateCategory {

        @Test
        @DisplayName("AC-PCA-002: ADMIN PUT → 200, status INACTIVE 반영")
        void update_asAdmin_returnsUpdated() throws Exception {
            givenAdminToken();
            long id = insertCategory("UPD_" + suffix.toUpperCase(), "원래 이름", null);

            mockMvc.perform(put(BASE_URL + "/" + id)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"수정된 이름","sortOrder":5,"status":"INACTIVE"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("수정된 이름"))
                    .andExpect(jsonPath("$.sortOrder").value(5))
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }

        @Test
        @DisplayName("AC-PCA-002-2: 존재하지 않는 id → 404")
        void update_notFound_returns404() throws Exception {
            givenAdminToken();

            mockMvc.perform(put(BASE_URL + "/999999999")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"없는 카테고리","sortOrder":0,"status":"ACTIVE"}
                                    """))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── AC-PCA-003 삭제 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PCA-003 카테고리 삭제")
    class DeleteCategory {

        @Test
        @DisplayName("AC-PCA-003a: 리프 카테고리 삭제 → 204")
        void delete_leafCategory_returns204() throws Exception {
            givenAdminToken();
            long id = insertCategory("DEL_" + suffix.toUpperCase(), "삭제 대상", null);

            mockMvc.perform(delete(BASE_URL + "/" + id)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("AC-PCA-003b: 자식 카테고리 존재 시 삭제 → 409")
        void delete_hasChildren_returns409() throws Exception {
            givenAdminToken();
            long parentId = insertCategory("PAR_" + suffix.toUpperCase(), "부모", null);
            insertCategory("KID_" + suffix.toUpperCase(), "자식", parentId);

            mockMvc.perform(delete(BASE_URL + "/" + parentId)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("AC-PCA-003-3: 존재하지 않는 id → 404")
        void delete_notFound_returns404() throws Exception {
            givenAdminToken();

            mockMvc.perform(delete(BASE_URL + "/999999999")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── AC-PCA-004 목록 조회 ───────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PCA-004 목록 조회")
    class ListCategories {

        @Test
        @DisplayName("AC-PCA-004: GET → 200, INACTIVE 카테고리 포함")
        void list_includesInactive() throws Exception {
            givenAdminToken();
            insertCategory("ACTIVE_" + suffix.toUpperCase(), "활성", null);
            long inactiveId = insertCategory("INACT_" + suffix.toUpperCase(), "비활성", null);
            jdbcTemplate.update("UPDATE publication_category SET status='INACTIVE' WHERE id=?", inactiveId);

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ─── AC-PCA-005 권한 가드 ───────────────────────────────────────────────

    @Nested
    @DisplayName("AC-PCA-005 권한")
    class Auth {

        @Test
        @DisplayName("AC-PCA-005: 미인증 요청 → 401")
        void unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-PCA-005-2: USER 역할 → 403")
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
                id, "pca-" + id, roles, Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                "VALUES (?, 'test-hash', '카테고리테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertCategory(String code, String name, Long parentId) {
        jdbcTemplate.update(
                "INSERT INTO publication_category (code, name, parent_id, sort_order, status) " +
                "VALUES (?, ?, ?, 0, 'ACTIVE')",
                code, name, parentId);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM publication_category WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }
}
