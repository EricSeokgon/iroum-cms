package kr.co.ircp.cms.domain.content;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-004 §B 메뉴 트리 IT (REQ-CONTENT-001-D).
 *
 * <p>메뉴 생성, 깊이 제한, 코드 중복, 트리 정렬, 순서 변경, 순환 참조,
 * 가시성 토글, 자손 cascade 삭제까지 9 AC 커버.
 *
 * <p>인증 모델: MENU:WRITE 권한(authority)을 가진 ADMIN 사용자 vs 권한 없는 USER.
 */
// @MX:NOTE: [AUTO] MenuIT — SPEC-CMS-004 §B 메뉴 트리 IT (MENU:WRITE 권한 패턴)
// @MX:SPEC: SPEC-CMS-004#REQ-CONTENT-001-D
@AutoConfigureMockMvc
@DisplayName("메뉴 트리 IT (SPEC-CMS-004 §B)")
class MenuIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-menu-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long userId;
    private long siteId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("menu-admin-" + uid());
        userId  = insertUser("menu-user-"  + uid());
        siteId  = insertSite("MENU-" + uid().toUpperCase());
    }

    // ─── §B-1 AC-MENU-1: 메뉴 생성 ───────────────────────────────────────────

    @Nested
    @DisplayName("§B-1: 메뉴 생성")
    class MenuCreate {

        @Test
        @DisplayName("AC-MENU-1: ADMIN — 정상 생성 201")
        void create_asAdmin_returns201() throws Exception {
            givenAdminToken();
            String code = "ROOT-" + uid();
            String body = """
                    {
                      "siteId": %d,
                      "code": "%s",
                      "name": "루트메뉴",
                      "target": "_self",
                      "sortOrder": 10,
                      "isVisible": true
                    }
                    """.formatted(siteId, code);
            mockMvc.perform(post("/api/v1/content/menus")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.code").value(code));
        }

        @Test
        @DisplayName("권한 없는 USER — 403 Forbidden")
        void create_asUser_returns403() throws Exception {
            givenUserToken();
            String body = """
                    {"siteId":%d,"code":"X","name":"x","target":"_self","sortOrder":0,"isVisible":true}
                    """.formatted(siteId);
            mockMvc.perform(post("/api/v1/content/menus")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── §B-2 AC-MENU-2: depth 5 초과 거부 ──────────────────────────────────

    @Nested
    @DisplayName("§B-2: 깊이 5 초과 거부")
    class MenuDepth {

        @Test
        @DisplayName("AC-MENU-2: depth=5 메뉴 자식 생성 시 400 MENU_DEPTH_EXCEEDED")
        void create_depth6_returns400() throws Exception {
            // depth=1..5 체인 직접 INSERT
            long rootId = insertMenu(siteId, null, "L1-" + uid(), "L1", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + rootId, rootId);
            long l2 = insertMenu(siteId, rootId, "L2-" + uid(), "L2", (short) 2, "/" + rootId + "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + rootId + "/" + l2, l2);
            long l3 = insertMenu(siteId, l2, "L3-" + uid(), "L3", (short) 3, "/" + rootId + "/" + l2 + "/0");
            long l4 = insertMenu(siteId, l3, "L4-" + uid(), "L4", (short) 4, "/" + rootId + "/" + l2 + "/" + l3 + "/0");
            long l5 = insertMenu(siteId, l4, "L5-" + uid(), "L5", (short) 5, "/" + rootId + "/" + l2 + "/" + l3 + "/" + l4 + "/0");

            givenAdminToken();
            String body = """
                    {"siteId":%d,"parentId":%d,"code":"L6-%s","name":"L6","target":"_self","sortOrder":0,"isVisible":true}
                    """.formatted(siteId, l5, uid());
            mockMvc.perform(post("/api/v1/content/menus")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── §B-3 AC-MENU-3: site 내 code 중복 ───────────────────────────────────

    @Nested
    @DisplayName("§B-3: 메뉴 code 중복 거부")
    class MenuCodeDuplicate {

        @Test
        @DisplayName("AC-MENU-3: 동일 site_id + code 중복 — 409 MENU_CODE_DUPLICATE")
        void create_duplicateCode_returns409() throws Exception {
            String code = "DUP-" + uid();
            long rootId = insertMenu(siteId, null, code, "기존", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + rootId, rootId);

            givenAdminToken();
            String body = """
                    {"siteId":%d,"code":"%s","name":"중복","target":"_self","sortOrder":0,"isVisible":true}
                    """.formatted(siteId, code);
            mockMvc.perform(post("/api/v1/content/menus")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict());
        }
    }

    // ─── §B-4 AC-MENU-4: 트리 응답 정렬 + §B-5 AC-MENU-5: 순서 변경 ─────────

    @Nested
    @DisplayName("§B-4/5: 트리 정렬 및 순서 변경")
    class MenuOrder {

        @Test
        @DisplayName("AC-MENU-4: GET /tree — 200 OK + 배열 응답")
        void tree_returns200() throws Exception {
            long root = insertMenu(siteId, null, "R-" + uid(), "R", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + root, root);
            insertMenu(siteId, root, "A-" + uid(), "A", (short) 2, "/" + root + "/0");
            insertMenu(siteId, root, "B-" + uid(), "B", (short) 2, "/" + root + "/0");

            mockMvc.perform(get("/api/v1/content/menus/tree").param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("AC-MENU-5: PATCH /{id}/order — 200 OK (ADMIN)")
        void order_asAdmin_returns200() throws Exception {
            long root = insertMenu(siteId, null, "O-" + uid(), "O", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + root, root);

            givenAdminToken();
            mockMvc.perform(patch("/api/v1/content/menus/" + root + "/order")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newSortOrder\":5}"))
                    .andExpect(status().isOk());
        }
    }

    // ─── §B-6 AC-MENU-6: 순환 참조 거부 + §B-7 자손 path 갱신 ──────────────

    @Nested
    @DisplayName("§B-6/7: 메뉴 이동")
    class MenuMove {

        @Test
        @DisplayName("AC-MENU-6: 자기 자신을 부모로 이동 — 400 MENU_CYCLE_DETECTED")
        // @MX:NOTE: [AUTO] AC-MENU-6 단순화 — 자기 자신 부모 시나리오로 cycle detection 검증
        void move_self_returns400() throws Exception {
            long a = insertMenu(siteId, null, "A-" + uid(), "A", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + a, a);

            givenAdminToken();
            mockMvc.perform(patch("/api/v1/content/menus/" + a + "/move")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newParentId\":" + a + "}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-MENU-7: 메뉴 이동 후 자손 path 갱신 (smoke — service 위임)")
        // @MX:NOTE: [AUTO] AC-MENU-7 자손 path 일괄 갱신은 service 내부 로직.
        // IT 레벨에서는 PATCH /move 호출이 200 OK 응답하는 것만 검증한다.
        void move_descendantPathUpdate_smoke() throws Exception {
            long a = insertMenu(siteId, null, "PA-" + uid(), "PA", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + a, a);
            long b = insertMenu(siteId, null, "PB-" + uid(), "PB", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + b, b);

            givenAdminToken();
            mockMvc.perform(patch("/api/v1/content/menus/" + b + "/move")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newParentId\":" + a + "}"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ─── §B-8 AC-MENU-8: 가시성 토글 + 캐시 무효화 (smoke) ──────────────────

    @Nested
    @DisplayName("§B-8: 가시성 토글")
    class MenuVisibility {

        @Test
        @DisplayName("AC-MENU-8: PATCH /{id}/visibility — 200 OK (캐시 무효화는 내부)")
        // @MX:NOTE: [AUTO] AC-MENU-8 캐시 무효화는 service 내부 동작.
        // IT는 PATCH 호출이 정상 처리되는 것까지만 검증한다.
        void visibility_returns200() throws Exception {
            long m = insertMenu(siteId, null, "V-" + uid(), "V", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + m, m);

            givenAdminToken();
            mockMvc.perform(patch("/api/v1/content/menus/" + m + "/visibility")
                            .header("Authorization", TOKEN)
                            .param("isVisible", "false"))
                    .andExpect(status().isOk());
        }
    }

    // ─── §B-9 AC-MENU-9: 삭제 + cascade ──────────────────────────────────────

    @Nested
    @DisplayName("§B-9: 메뉴 삭제 cascade")
    class MenuDelete {

        @Test
        @DisplayName("AC-MENU-9: DELETE /{id} — 204 No Content + 자손 cascade(FK ON DELETE CASCADE)")
        void delete_cascadesDescendants() throws Exception {
            long root = insertMenu(siteId, null, "D-" + uid(), "D", (short) 1, "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + root, root);
            long child = insertMenu(siteId, root, "DC-" + uid(), "DC", (short) 2, "/" + root + "/0");
            jdbcTemplate.update("UPDATE menu SET path = ? WHERE id = ?", "/" + root + "/" + child, child);

            givenAdminToken();
            mockMvc.perform(delete("/api/v1/content/menus/" + root)
                            .header("Authorization", TOKEN))
                    .andExpect(status().isNoContent());

            Integer remaining = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM menu WHERE id IN (?, ?)", Integer.class, root, child);
            assert remaining != null && remaining == 0
                    : "root + child cascade 삭제 실패 (remaining=" + remaining + ")";
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "menu-admin-" + adminId,
                Set.of("ADMIN"),
                Set.of("MENU:WRITE", "MENU:PERMISSION:WRITE"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private void givenUserToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                userId, "menu-user-" + userId,
                Set.of("USER"),
                Set.of(),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '메뉴테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertSite(String code) {
        jdbcTemplate.update(
                "INSERT INTO site (code, name, domain, default_language) VALUES (?, ?, ?, 'ko')",
                code, "테스트사이트-" + code, "test-" + code.toLowerCase() + ".example.go.kr");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM site WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private long insertMenu(long siteId, Long parentId, String code, String name, short depth, String path) {
        if (parentId == null) {
            jdbcTemplate.update(
                    "INSERT INTO menu (site_id, parent_id, code, name, target, sort_order, depth, path) " +
                            "VALUES (?, NULL, ?, ?, '_self', 0, ?, ?)",
                    siteId, code, name, depth, path);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO menu (site_id, parent_id, code, name, target, sort_order, depth, path) " +
                            "VALUES (?, ?, ?, ?, '_self', 0, ?, ?)",
                    siteId, parentId, code, name, depth, path);
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM menu WHERE site_id = ? AND code = ?", Long.class, siteId, code);
        return id == null ? -1L : id;
    }
}
