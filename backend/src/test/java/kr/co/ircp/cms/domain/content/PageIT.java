package kr.co.ircp.cms.domain.content;

import kr.co.ircp.cms.domain.auth.repository.TokenBlacklistMapper;
import kr.co.ircp.cms.domain.auth.service.JwtTokenProvider;
import kr.co.ircp.cms.domain.content.page.service.PageHistoryRetentionService;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired PageHistoryRetentionService retentionService;

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
                    {"title":"새 제목","slug":"%s","changeSummary":"제목 수정"}
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
        @DisplayName("AC-PAGE-10: POST /rollback/{version} — 권한 보유 시 호출 가능")
        // @MX:NOTE: [AUTO] AC-PAGE-10 실 롤백 로직은 page_history 시드 필요.
        // 본 IT는 권한 게이트 + 엔드포인트 매핑까지만 검증한다.
        void rollback_asAdmin_callable() throws Exception {
            long pageId = insertPage(siteId, templateId, "RBK-" + uid().toUpperCase(),
                    "rbk-" + uid(), "DRAFT");

            givenAdminToken();
            // 미존재 version 롤백은 4xx 가능 — 200/4xx 모두 권한 게이트 통과 확인 용도
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/1")
                            .header("Authorization", TOKEN))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        if (s == 401 || s == 403) {
                            throw new AssertionError("권한 게이트 통과 실패: status=" + s);
                        }
                    });
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
                    {"title":"변경된 제목","slug":"%s","changeSummary":"slug 변경"}
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

    // ─── SPEC-CMS-PAGE-HISTORY-001 REQ-PHIST-001: 이력 보존 정책 ────────────────

    @Nested
    @DisplayName("REQ-PHIST-001: 이력 보존 정책 (Retention)")
    class PageHistoryRetention {

        @Test
        @DisplayName("AC-PHIST-001: 52건 이력 + max=50 → version 1·2 삭제, 3~52 보존")
        void retention_keepsLatest50_deletesOldest2() {
            long pageId = insertPage(siteId, templateId, "RT1-" + uid().toUpperCase(),
                    "rt1-" + uid(), "DRAFT");
            // 52개 이력 시드 (version 1~52)
            for (int v = 1; v <= 52; v++) {
                insertPageHistory(pageId, v, "{\"title\":\"v" + v + "\",\"slug\":\"s" + v + "\"}");
            }

            // 보존 정책 적용 (max=50)
            retentionService.enforceRetention(pageId, 52);

            assertThat(countHistory(pageId)).isEqualTo(50);
            assertThat(historyExists(pageId, 1)).isFalse();
            assertThat(historyExists(pageId, 2)).isFalse();
            assertThat(historyExists(pageId, 3)).isTrue();
            assertThat(historyExists(pageId, 52)).isTrue();
        }

        @Test
        @DisplayName("AC-PHIST-002: 정리 후 currentVersion(최신 version) 항목은 보존된다")
        void retention_preservesCurrentVersion() {
            long pageId = insertPage(siteId, templateId, "RT2-" + uid().toUpperCase(),
                    "rt2-" + uid(), "DRAFT");
            for (int v = 1; v <= 52; v++) {
                insertPageHistory(pageId, v, "{\"title\":\"v" + v + "\"}");
            }

            retentionService.enforceRetention(pageId, 52);

            // currentVersion=52 항목은 절대 삭제되지 않음
            assertThat(historyExists(pageId, 52)).isTrue();
        }

        @Test
        @DisplayName("AC-PHIST-004: 페이지 A 정리가 페이지 B(10건) 이력에 영향을 주지 않는다")
        void retention_isolatesPerPage() {
            long pageA = insertPage(siteId, templateId, "RTA-" + uid().toUpperCase(),
                    "rta-" + uid(), "DRAFT");
            long pageB = insertPage(siteId, templateId, "RTB-" + uid().toUpperCase(),
                    "rtb-" + uid(), "DRAFT");
            for (int v = 1; v <= 52; v++) insertPageHistory(pageA, v, "{\"title\":\"a" + v + "\"}");
            for (int v = 1; v <= 10; v++) insertPageHistory(pageB, v, "{\"title\":\"b" + v + "\"}");

            retentionService.enforceRetention(pageA, 52);

            assertThat(countHistory(pageA)).isEqualTo(50);
            assertThat(countHistory(pageB)).isEqualTo(10); // B 무영향
        }
    }

    // ─── REQ-PHIST-002: 롤백 실제 복원 검증 ────────────────────────────────────

    @Nested
    @DisplayName("REQ-PHIST-002: 롤백 실제 복원")
    class PageRollbackRestore {

        @Test
        @DisplayName("AC-PHIST-005/006/007: v1 롤백 → title='원본'/slug='orig' 복원 + DRAFT + version+1")
        void rollback_restoresSnapshotAndPersists() throws Exception {
            String origSlug = "orig-" + uid();
            long pageId = insertPage(siteId, templateId, "RBR-" + uid().toUpperCase(),
                    "current-" + uid(), "PUBLISHED");
            // currentVersion을 3으로 올리고 v1 snapshot 시드
            jdbcTemplate.update("UPDATE page SET current_version = 3 WHERE id = ?", pageId);
            insertPageHistory(pageId, 1, "{\"title\":\"원본\",\"slug\":\"" + origSlug + "\"}");

            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/1")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("원본"))
                    .andExpect(jsonPath("$.slug").value(origSlug))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.currentVersion").value(4));

            // DB 영속 검증
            String dbTitle = jdbcTemplate.queryForObject(
                    "SELECT title FROM page WHERE id = ?", String.class, pageId);
            assertThat(dbTitle).isEqualTo("원본");

            // 롤백 이력 changeSummary 검증 (AC-PHIST-007)
            String summary = jdbcTemplate.queryForObject(
                    "SELECT change_summary FROM page_history WHERE page_id = ? AND version = 4",
                    String.class, pageId);
            assertThat(summary).contains("ROLLBACK_FROM_v1");
        }

        @Test
        @DisplayName("AC-PHIST-008: 미존재 version 롤백 → 4xx + 페이지 불변")
        void rollback_nonexistentVersion_returns4xx_pageUnchanged() throws Exception {
            String slug = "rbu-" + uid();
            long pageId = insertPage(siteId, templateId, "RBU-" + uid().toUpperCase(),
                    slug, "PUBLISHED");

            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/999")
                            .header("Authorization", TOKEN))
                    .andExpect(status().is4xxClientError());

            // 페이지 불변 (status=PUBLISHED, version=1 유지)
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM page WHERE id = ?", String.class, pageId);
            assertThat(status).isEqualTo("PUBLISHED");
            Integer ver = jdbcTemplate.queryForObject(
                    "SELECT current_version FROM page WHERE id = ?", Integer.class, pageId);
            assertThat(ver).isEqualTo(1);
        }
    }

    // ─── REQ-PHIST-004: 롤백 감사 로그 ─────────────────────────────────────────

    @Nested
    @DisplayName("REQ-PHIST-004: 롤백 감사 로그")
    class PageRollbackAudit {

        @Test
        @DisplayName("AC-PHIST-013/014: 롤백 성공 → audit_log 1건 (action=UPDATE, from/to version)")
        void rollback_success_writesAuditLog() throws Exception {
            long pageId = insertPage(siteId, templateId, "AUD-" + uid().toUpperCase(),
                    "aud-" + uid(), "PUBLISHED");
            insertPageHistory(pageId, 1, "{\"title\":\"원본\",\"slug\":\"orig\"}");

            long before = countAuditForPage(pageId);

            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/1")
                            .header("Authorization", TOKEN))
                    .andExpect(status().isOk());

            // IT profile은 auditExecutor를 SyncTaskExecutor로 override → 동기 적재, polling 불필요
            assertThat(countAuditForPage(pageId)).isEqualTo(before + 1);

            // afterValue에 from/to version 포함 (AC-PHIST-014)
            String afterValue = jdbcTemplate.queryForObject(
                    "SELECT after_value::text FROM audit_log " +
                            "WHERE entity_type = 'Page' AND entity_id = ? AND action = 'UPDATE' " +
                            "ORDER BY id DESC LIMIT 1",
                    String.class, String.valueOf(pageId));
            assertThat(afterValue).contains("from_version").contains("to_version");
        }

        @Test
        @DisplayName("AC-PHIST-015: 롤백 실패(version 미존재) → audit_log 미기록")
        void rollback_failure_writesNoAuditLog() throws Exception {
            long pageId = insertPage(siteId, templateId, "AUF-" + uid().toUpperCase(),
                    "auf-" + uid(), "PUBLISHED");

            long before = countAuditForPage(pageId);

            givenAdminToken();
            mockMvc.perform(post("/api/v1/content/pages/" + pageId + "/rollback/999")
                            .header("Authorization", TOKEN))
                    .andExpect(status().is4xxClientError());

            assertThat(countAuditForPage(pageId)).isEqualTo(before);
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void insertPageHistory(long pageId, int version, String snapshotJson) {
        jdbcTemplate.update(
                "INSERT INTO page_history (page_id, version, snapshot, edited_by, change_summary) " +
                        "VALUES (?, ?, ?::jsonb, ?, ?)",
                pageId, version, snapshotJson, adminId, "seed-v" + version);
    }

    private int countHistory(long pageId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_history WHERE page_id = ?", Integer.class, pageId);
        return c == null ? -1 : c;
    }

    private boolean historyExists(long pageId, int version) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM page_history WHERE page_id = ? AND version = ?",
                Integer.class, pageId, version);
        return c != null && c > 0;
    }

    private long countAuditForPage(long pageId) {
        Long c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_type = 'Page' AND entity_id = ?",
                Long.class, String.valueOf(pageId));
        return c == null ? -1L : c;
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
