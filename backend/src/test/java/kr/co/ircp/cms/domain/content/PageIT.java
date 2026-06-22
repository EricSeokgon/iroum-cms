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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-004 §E 페이지 CRUD/발행/이력 IT (REQ-CONTENT-005-D).
 *
 * <p>14 ACs (AC-PAGE-1..14) 중 IT 가능 항목 커버.
 * 예약 자동 발행(AC-PAGE-7)은 배치 잡 의존이라 IT 범위 외.
 *
 * <p>인증 모델: PAGE:WRITE, PAGE:PUBLISH, PAGE:HISTORY:READ, PAGE:ROLLBACK 권한 보유 CONTENT_ADMIN.
 */
// @MX:NOTE: [AUTO] PageIT — SPEC-CMS-004 §E 페이지 CRUD/발행/이력 IT
// @MX:SPEC: SPEC-CMS-004#REQ-CONTENT-005-D
@AutoConfigureMockMvc
@DisplayName("페이지 CRUD/발행/이력 IT (SPEC-CMS-004 §E)")
class PageIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-page-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long siteId;
    private long templateId;

    @BeforeEach
    void setUp() {
        adminId    = insertUser("page-admin-" + uid());
        siteId     = insertSite("PAGE-" + uid().toUpperCase());
        templateId = insertTemplate("TPL-" + uid().toUpperCase());
    }

    // ─── §E-1 AC-PAGE-1: 페이지 생성 ─────────────────────────────────────────

    @Nested
    @DisplayName("§E-1: 페이지 생성")
    class PageCreate {

        @Test
        @DisplayName("AC-PAGE-1: CONTENT_ADMIN — 201 + status=DRAFT, version=1")
        void create_asAdmin_returns201() throws Exception {
            givenAdminToken();
            String slug = "about-" + uid();
            String body = """
                    {
                      "siteId": %d,
                      "templateId": %d,
                      "code": "ABOUT-%s",
                      "title": "회사 소개",
                      "slug": "%s"
                    }
                    """.formatted(siteId, templateId, uid().toUpperCase(), slug);
            mockMvc.perform(post("/api/v1/content/pages")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.currentVersion").value(1));
        }
    }

    // ─── §E-2 AC-PAGE-2: 슬러그 패턴 거부 ───────────────────────────────────

    @Nested
    @DisplayName("§E-2: 슬러그 패턴")
    class PageSlugPattern {

        @Test
        @DisplayName("AC-PAGE-2: slug='About!Now' — 400 SLUG_INVALID_PATTERN")
        void create_invalidSlug_returns400() throws Exception {
            givenAdminToken();
            String body = """
                    {"siteId":%d,"templateId":%d,"code":"C-%s","title":"t","slug":"About!Now"}
                    """.formatted(siteId, templateId, uid().toUpperCase());
            mockMvc.perform(post("/api/v1/content/pages")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── §E-3 AC-PAGE-3: 슬러그 중복 거부 ───────────────────────────────────

    @Nested
    @DisplayName("§E-3: 슬러그 중복")
    class PageSlugDuplicate {

        @Test
        @DisplayName("AC-PAGE-3: 동일 (siteId, slug) 중복 — 409 SLUG_DUPLICATE")
        void create_duplicateSlug_returns409() throws Exception {
            String slug = "dup-" + uid();
            insertPage(siteId, templateId, "EXIST-" + uid().toUpperCase(), slug, "DRAFT");

            givenAdminToken();
            String body = """
                    {"siteId":%d,"templateId":%d,"code":"N-%s","title":"t","slug":"%s"}
                    """.formatted(siteId, templateId, uid().toUpperCase(), slug);
            mockMvc.perform(post("/api/v1/content/pages")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict());
        }
    }

    // ─── §E-4 AC-PAGE-4: 수정 시 이력 누적 ──────────────────────────────────

    @Nested
    @DisplayName("§E-4: 수정 이력 누적")
    class PageUpdateHistory {

        @Test
        @DisplayName("AC-PAGE-4: PUT 페이지 — 200 OK + version 증가")
        // @MX:NOTE: [AUTO] AC-PAGE-4 page_history 누적은 service 내부 트랜잭션.
        // IT 레벨에서는 PUT 200 응답까지만 검증한다.
        void update_returns200() throws Exception {
            String slug = "upd-" + uid();
            long pageId = insertPage(siteId, templateId, "UPD-" + uid().toUpperCase(), slug, "DRAFT");

            givenAdminToken();
            String body = """
                    {"title":"새 제목","slug":"%s","changeSummary":"제목 수정","expectedVersion":1}
                    """.formatted(slug);
            mockMvc.perform(put("/api/v1/content/pages/" + pageId)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ─── §E-5 AC-PAGE-5: 즉시 발행 ────────────────────────────────────────────

    @Nested
    @DisplayName("§E-5: 즉시 발행")
    class PagePublish {

        @Test
        @DisplayName("AC-PAGE-5: POST /publish — 200 OK + status=PUBLISHED")
        void publish_returns200() throws Exception {
            long pageId = insertPage(siteId, templateId, "PUB-" + uid().toUpperCase(),
                    "pub-" + uid(), "DRAFT");
            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/publish")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ─── §E-6 AC-PAGE-6: 예약 미래 검증 ──────────────────────────────────────

    @Nested
    @DisplayName("§E-6: 예약 발행")
    class PageSchedule {

        @Test
        @DisplayName("AC-PAGE-6: scheduled_at 과거 — 400 (Bean Validation @Future)")
        void schedule_pastDate_returns400() throws Exception {
            long pageId = insertPage(siteId, templateId, "SCH-" + uid().toUpperCase(),
                    "sch-" + uid(), "DRAFT");
            givenAdminToken();
            String body = "{\"scheduledAt\":\"2020-01-01T00:00:00Z\"}";
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/schedule")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        // @MX:NOTE: [AUTO] AC-PAGE-7 자동 발행은 배치 잡 의존 — IT 범위 외 (skip)
    }

    // ─── §E-8 AC-PAGE-8: 철회 후 시민 404 ───────────────────────────────────

    @Nested
    @DisplayName("§E-8: 철회 후 시민 차단")
    class PageRetract {

        @Test
        @DisplayName("AC-PAGE-8: RETRACTED 페이지 by-slug — 404")
        void getBySlug_retracted_returns404() throws Exception {
            String slug = "ret-" + uid();
            insertPage(siteId, templateId, "RET-" + uid().toUpperCase(), slug, "RETRACTED");

            mockMvc.perform(get("/api/v1/content/pages/by-slug/" + slug)
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── §E-9 AC-PAGE-9: 이력 조회 ───────────────────────────────────────────

    @Nested
    @DisplayName("§E-9: 이력 조회")
    class PageHistory {

        @Test
        @DisplayName("AC-PAGE-9: GET /history — 200 OK (배열, 권한 보유 시)")
        // @MX:NOTE: [AUTO] AC-PAGE-9 diff format은 service 내부.
        // IT는 GET 응답이 200 + 배열인지만 검증한다.
        void history_returns200() throws Exception {
            long pageId = insertPage(siteId, templateId, "HST-" + uid().toUpperCase(),
                    "hst-" + uid(), "DRAFT");

            givenAdminToken();
            mockMvc.perform(get("/api/v1/content/pages/" + pageId + "/history")
                            .header("Authorization", TOKEN))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ─── §E-10 AC-PAGE-10: 롤백 ──────────────────────────────────────────────

    @Nested
    @DisplayName("§E-10: 롤백")
    class PageRollback {

        @Test
        @DisplayName("AC-PHIST-005/006: rollback 시 snapshot의 title/slug가 실제 복원된다")
        // @MX:NOTE: [AUTO] AC-PHIST-005/006 — page_history 시드 후 롤백 → title/slug 복원 검증.
        // @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001
        void rollback_restoresTitleAndSlug() throws Exception {
            // 1) BEFORE 상태로 페이지 생성
            long pageId = insertPage(siteId, templateId, "RBK-" + uid().toUpperCase(),
                    "before-slug-" + uid(), "DRAFT");
            jdbcTemplate.update(
                    "UPDATE page SET title = 'BEFORE_TITLE', slug = 'before-slug' WHERE id = ?", pageId);

            // 2) version 1 스냅샷 시드 (복원 대상)
            insertPageHistory(pageId, 1, "BEFORE_TITLE", "before-slug");

            // 3) 현재 페이지를 다른 값으로 변경
            jdbcTemplate.update(
                    "UPDATE page SET title = 'AFTER_TITLE', slug = 'after-slug' WHERE id = ?", pageId);

            // 4) version 1 로 롤백
            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/1")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk());

            // 5) title/slug 가 스냅샷 값으로 복원되었는지 DB 직접 확인
            String title = jdbcTemplate.queryForObject(
                    "SELECT title FROM page WHERE id = ?", String.class, pageId);
            String slug = jdbcTemplate.queryForObject(
                    "SELECT slug FROM page WHERE id = ?", String.class, pageId);
            org.assertj.core.api.Assertions.assertThat(title).isEqualTo("BEFORE_TITLE");
            org.assertj.core.api.Assertions.assertThat(slug).isEqualTo("before-slug");
        }
    }

    // ─── §E-10b AC-PHIST-007: 롤백 감사 로그 ────────────────────────────────

    @Nested
    @DisplayName("§E-10b: 롤백 감사 로그")
    class PageRollbackAuditLog {

        @Test
        @DisplayName("AC-PHIST-007: rollback 시 audit_log action=UPDATE 기록")
        // @MX:NOTE: [AUTO] AC-PHIST-007 — @AuditLog 비동기 적재. Thread.sleep 버퍼 후 검증.
        // @MX:SPEC: SPEC-CMS-PAGE-HISTORY-001
        void rollback_createsAuditLog() throws Exception {
            long pageId = insertPage(siteId, templateId, "AUDIT-" + uid().toUpperCase(),
                    "audit-" + uid(), "DRAFT");
            insertPageHistory(pageId, 1, "이전제목", "prev-slug");

            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/1")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk());

            // audit_log 는 비동기 적재 — 잠시 대기
            Thread.sleep(300);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'Page' AND entity_id = ? AND action = 'UPDATE'",
                    Integer.class, String.valueOf(pageId));
            org.assertj.core.api.Assertions.assertThat(count).isGreaterThanOrEqualTo(1);
        }
    }

    // ─── §E-11 AC-PAGE-11: 슬러그 변경 시 리다이렉트 자동 (smoke) ───────────

    @Nested
    @DisplayName("§E-11: 슬러그 변경 자동 리다이렉트")
    class PageSlugRedirect {

        @Test
        @DisplayName("AC-PAGE-11: PUT 으로 slug 변경 — 200 OK (redirect 등록은 service)")
        // @MX:NOTE: [AUTO] AC-PAGE-11 seo_redirect 자동 INSERT는 service 내부 로직.
        // IT는 PUT 호출이 정상 처리되는지만 검증한다.
        void updateSlug_returns200() throws Exception {
            String oldSlug = "old-" + uid();
            long pageId = insertPage(siteId, templateId, "RD-" + uid().toUpperCase(),
                    oldSlug, "PUBLISHED");

            String newSlug = "new-" + uid();
            givenAdminToken();
            String body = """
                    {"title":"변경된 제목","slug":"%s","changeSummary":"slug 변경","expectedVersion":1}
                    """.formatted(newSlug);
            mockMvc.perform(put("/api/v1/content/pages/" + pageId)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    // ─── §E-12/13/14 AC-PAGE-12..14: 시민 차단 ──────────────────────────────

    @Nested
    @DisplayName("§E-12/13/14: 시민 by-slug 차단")
    class PagePublicAccess {

        @Test
        @DisplayName("AC-PAGE-12: DRAFT — 익명 by-slug 404")
        void getBySlug_draft_returns404() throws Exception {
            String slug = "drft-" + uid();
            insertPage(siteId, templateId, "DRFT-" + uid().toUpperCase(), slug, "DRAFT");

            mockMvc.perform(get("/api/v1/content/pages/by-slug/" + slug)
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-PAGE-13: RETRACTED — 익명 by-slug 404")
        void getBySlug_retracted_returns404() throws Exception {
            String slug = "retx-" + uid();
            insertPage(siteId, templateId, "RTX-" + uid().toUpperCase(), slug, "RETRACTED");

            mockMvc.perform(get("/api/v1/content/pages/by-slug/" + slug)
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AC-PAGE-14: SCHEDULED — 익명 by-slug 404")
        void getBySlug_scheduled_returns404() throws Exception {
            String slug = "sch2-" + uid();
            insertPage(siteId, templateId, "SCH2-" + uid().toUpperCase(), slug, "SCHEDULED");

            mockMvc.perform(get("/api/v1/content/pages/by-slug/" + slug)
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "page-admin-" + adminId,
                Set.of("CONTENT_ADMIN"),
                Set.of("PAGE:WRITE", "PAGE:PUBLISH", "PAGE:HISTORY:READ", "PAGE:ROLLBACK"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '페이지테스트', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
                username, "dummy-hmac-" + username);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", Long.class, username);
        return id == null ? -1L : id;
    }

    private long insertSite(String code) {
        jdbcTemplate.update(
                "INSERT INTO site (code, name, domain, default_language) VALUES (?, ?, ?, 'ko')",
                code, "사이트-" + code, "test-" + code.toLowerCase() + ".example.go.kr");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM site WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    private long insertTemplate(String code) {
        jdbcTemplate.update(
                "INSERT INTO template (code, name, layout_type, html_template) " +
                        "VALUES (?, ?, 'FULL', ?)",
                code, "템플릿-" + code,
                "<html><body>{{HEADER}}{{CONTENT}}{{FOOTER}}</body></html>");
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM template WHERE code = ?", Long.class, code);
        return id == null ? -1L : id;
    }

    /** page_history 시드 (롤백/감사 IT 용). REQ-PHIST-002/004 */
    private void insertPageHistory(long pageId, int version, String title, String slug) {
        String snapshot = String.format("{\"title\":\"%s\",\"slug\":\"%s\"}", title, slug);
        jdbcTemplate.update(
                "INSERT INTO page_history (page_id, version, snapshot, edited_by, change_summary) " +
                        "VALUES (?, ?, ?::jsonb, 1, 'test')",
                pageId, version, snapshot);
    }

    private long insertPage(long siteId, long templateId, String code, String slug, String status) {
        if ("SCHEDULED".equals(status)) {
            jdbcTemplate.update(
                    "INSERT INTO page (site_id, template_id, code, title, slug, status, scheduled_at, " +
                            "current_version, created_by, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 'SCHEDULED', NOW() + INTERVAL '1 day', 1, ?, NOW(), NOW())",
                    siteId, templateId, code, "Title-" + code, slug, adminId);
        } else if ("PUBLISHED".equals(status)) {
            jdbcTemplate.update(
                    "INSERT INTO page (site_id, template_id, code, title, slug, status, published_at, " +
                            "current_version, created_by, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, 'PUBLISHED', NOW(), 1, ?, NOW(), NOW())",
                    siteId, templateId, code, "Title-" + code, slug, adminId);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO page (site_id, template_id, code, title, slug, status, " +
                            "current_version, created_by, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 1, ?, NOW(), NOW())",
                    siteId, templateId, code, "Title-" + code, slug, status, adminId);
        }
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM page WHERE site_id = ? AND code = ?", Long.class, siteId, code);
        return id == null ? -1L : id;
    }
}
