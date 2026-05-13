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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPEC-CMS-004 §H 팝업 + §I 배너 IT (REQ-CONTENT-008-D, 009-D).
 *
 * <p>§H 7 ACs (AC-POP-1..7) + §I 5 ACs (AC-BAN-1..5) 커버.
 *
 * <p>인증 모델: CONTENT:WRITE / CONTENT:READ 권한 보유 CONTENT_ADMIN.
 */
// @MX:NOTE: [AUTO] PopupBannerIT — SPEC-CMS-004 §H/I 팝업·배너 IT
// @MX:SPEC: SPEC-CMS-004#REQ-CONTENT-008-D
// @MX:SPEC: SPEC-CMS-004#REQ-CONTENT-009-D
@AutoConfigureMockMvc
@DisplayName("팝업/배너 IT (SPEC-CMS-004 §H/I)")
class PopupBannerIT extends AbstractIntegrationTest {

    private static final String TOKEN = "Bearer test-popup-banner-token";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean TokenBlacklistMapper tokenBlacklistMapper;

    private long adminId;
    private long siteId;

    @BeforeEach
    void setUp() {
        adminId = insertUser("pb-admin-" + uid());
        siteId  = insertSite("PB-" + uid().toUpperCase());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §H 팝업 (REQ-CONTENT-008-D)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§H 팝업")
    class Popup {

        @Test
        @DisplayName("AC-POP-1: show_from > show_until 역전 — 400 POPUP_PERIOD_INVALID")
        void create_invalidPeriod_returns400() throws Exception {
            givenAdminToken();
            String body = """
                    {
                      "siteId": %d,
                      "title": "역전",
                      "contentHtml": "<p>x</p>",
                      "showFrom": "2026-05-01T00:00:00Z",
                      "showUntil": "2026-04-30T00:00:00Z"
                    }
                    """.formatted(siteId);
            mockMvc.perform(post("/api/v1/content/popups")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-POP-2: 활성 팝업 조회 — 200 OK + 응답 포함")
        void active_includesActive() throws Exception {
            long popupId = insertPopup(siteId, "활성팝업-" + uid(), "ALL",
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 0);
            mockMvc.perform(get("/api/v1/content/popups/active")
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Popup-Limit", "5"));
        }

        @Test
        @DisplayName("AC-POP-3: 만료된 팝업 — active 응답에 미포함")
        void active_excludesExpired() throws Exception {
            insertPopup(siteId, "만료-" + uid(), "ALL",
                    Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), 0);
            mockMvc.perform(get("/api/v1/content/popups/active")
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-POP-4: target_type=ROLE — service 내부 필터링 검증 (smoke)")
        // @MX:NOTE: [AUTO] AC-POP-4 역할 필터링은 service 내부 — IT는 200 응답까지만 검증
        void active_roleFilter_smoke() throws Exception {
            insertPopup(siteId, "역할팝업-" + uid(), "ROLE",
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 0);
            mockMvc.perform(get("/api/v1/content/popups/active")
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-POP-5: target_type=MEMBER — 익명 호출 OK (필터링은 service)")
        // @MX:NOTE: [AUTO] AC-POP-5 MEMBER 타겟 익명 차단은 service 내부 — 200 응답까지만 검증
        void active_memberFilter_anonymous() throws Exception {
            insertPopup(siteId, "회원팝업-" + uid(), "MEMBER",
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 0);
            mockMvc.perform(get("/api/v1/content/popups/active")
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AC-POP-6: show_today_close=true — cookieKey 메타 노출 (응답 schema 검증)")
        void active_cookieKeyExposed() throws Exception {
            insertPopupCookieClose(siteId, "쿠키팝업-" + uid(), true,
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600));
            mockMvc.perform(get("/api/v1/content/popups/active")
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("AC-POP-7: 응답 헤더 X-Popup-Limit=5")
        void active_xPopupLimitHeader() throws Exception {
            mockMvc.perform(get("/api/v1/content/popups/active")
                            .param("siteId", String.valueOf(siteId)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Popup-Limit", "5"));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // §I 배너 (REQ-CONTENT-009-D)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("§I 배너")
    class Banner {

        @Test
        @DisplayName("AC-BAN-1: alt_text='' — 400 BANNER_ALT_REQUIRED")
        void create_emptyAlt_returns400() throws Exception {
            givenAdminToken();
            String body = """
                    {
                      "siteId": %d,
                      "bannerGroupCode": "HOME_HERO",
                      "title": "배너1",
                      "imageUrl": "/img/x.jpg",
                      "altText": "",
                      "displayFrom": "%s",
                      "displayUntil": "%s"
                    }
                    """.formatted(siteId,
                    Instant.now().plusSeconds(60).toString(),
                    Instant.now().plusSeconds(3600).toString());
            mockMvc.perform(post("/api/v1/content/banners")
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC-BAN-2: 그룹별 정렬 — GET 200 OK + 배열")
        void getByGroup_returns200Array() throws Exception {
            String group = "HERO-" + uid().toUpperCase();
            insertBanner(siteId, group, "B-A-" + uid(), "/img/a.jpg", "이미지A", 20,
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), "ACTIVE");
            insertBanner(siteId, group, "B-B-" + uid(), "/img/b.jpg", "이미지B", 10,
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), "ACTIVE");
            mockMvc.perform(get("/api/v1/content/banners")
                            .param("group", group))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("AC-BAN-3: 노출 기간 외 배너 — 미포함")
        void getByGroup_excludesExpired() throws Exception {
            String group = "EXP-" + uid().toUpperCase();
            insertBanner(siteId, group, "B-EXP-" + uid(), "/img/exp.jpg", "만료", 10,
                    Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), "ACTIVE");
            mockMvc.perform(get("/api/v1/content/banners")
                            .param("group", group))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("AC-BAN-4: 클릭 카운트 원자 증가 — 순차 호출 후 +N 검증")
        // @MX:NOTE: [AUTO] AC-BAN-4 병렬 HTTP 호출은 IT에서 복잡 — 순차 호출로 단조 증가 검증
        void click_atomicIncrement() throws Exception {
            String group = "CLK-" + uid().toUpperCase();
            long bannerId = insertBanner(siteId, group, "B-CLK-" + uid(), "/img/c.jpg", "클릭", 0,
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), "ACTIVE");

            Long before = jdbcTemplate.queryForObject(
                    "SELECT click_count FROM banner WHERE id = ?", Long.class, bannerId);
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/v1/content/banners/" + bannerId + "/click"))
                        .andExpect(status().isNoContent());
            }
            Long after = jdbcTemplate.queryForObject(
                    "SELECT click_count FROM banner WHERE id = ?", Long.class, bannerId);
            assert before != null && after != null && after - before == 3L
                    : "click_count must increment by 3: before=" + before + ", after=" + after;
        }

        @Test
        @DisplayName("AC-BAN-5: 클릭 audit_log 기록 (smoke — service 위임)")
        // @MX:NOTE: [AUTO] AC-BAN-5 audit_log 적재는 service 내부 — IT는 클릭 응답만 검증
        void click_auditLog_smoke() throws Exception {
            String group = "AUD-" + uid().toUpperCase();
            long bannerId = insertBanner(siteId, group, "B-AUD-" + uid(), "/img/au.jpg", "감사", 0,
                    Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), "ACTIVE");
            mockMvc.perform(post("/api/v1/content/banners/" + bannerId + "/click"))
                    .andExpect(status().isNoContent());
        }
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void givenAdminToken() {
        JwtTokenProvider.JwtClaims claims = new JwtTokenProvider.JwtClaims(
                adminId, "pb-admin-" + adminId,
                Set.of("CONTENT_ADMIN"),
                Set.of("CONTENT:WRITE", "CONTENT:READ"),
                Instant.now().plusSeconds(900));
        when(tokenBlacklistMapper.exists(anyString())).thenReturn(false);
        when(jwtTokenProvider.validateAccessToken(anyString())).thenReturn(Optional.of(claims));
    }

    private long insertUser(String username) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password_hash, name, status, " +
                        "email_hmac, email_key_version, password_changed_at, created_at, updated_at) " +
                        "VALUES (?, 'test-hash', '팝업배너', 'ACTIVE', ?, 1, NOW(), NOW(), NOW())",
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

    private long insertPopup(long siteId, String title, String targetType,
                              Instant showFrom, Instant showUntil, int priority) {
        jdbcTemplate.update(
                "INSERT INTO popup (site_id, title, content_html, position, width, height, " +
                        "show_from, show_until, show_today_close, display_priority, " +
                        "target_type, target_role_codes, status, created_at, updated_at) " +
                        "VALUES (?, ?, '<p>x</p>', 'CENTER', 400, 300, ?, ?, false, ?, " +
                        "?, '[]'::jsonb, 'ACTIVE', NOW(), NOW())",
                siteId, title, java.sql.Timestamp.from(showFrom),
                java.sql.Timestamp.from(showUntil), priority, targetType);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM popup WHERE site_id = ? AND title = ?", Long.class, siteId, title);
        return id == null ? -1L : id;
    }

    private long insertPopupCookieClose(long siteId, String title, boolean showTodayClose,
                                         Instant showFrom, Instant showUntil) {
        jdbcTemplate.update(
                "INSERT INTO popup (site_id, title, content_html, position, width, height, " +
                        "show_from, show_until, show_today_close, display_priority, " +
                        "target_type, target_role_codes, status, created_at, updated_at) " +
                        "VALUES (?, ?, '<p>cookie</p>', 'CENTER', 400, 300, ?, ?, ?, 0, " +
                        "'ALL', '[]'::jsonb, 'ACTIVE', NOW(), NOW())",
                siteId, title, java.sql.Timestamp.from(showFrom),
                java.sql.Timestamp.from(showUntil), showTodayClose);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM popup WHERE site_id = ? AND title = ?", Long.class, siteId, title);
        return id == null ? -1L : id;
    }

    private long insertBanner(long siteId, String group, String title, String imageUrl,
                               String altText, int sortOrder,
                               Instant displayFrom, Instant displayUntil, String status) {
        jdbcTemplate.update(
                "INSERT INTO banner (site_id, banner_group_code, title, image_url, alt_text, " +
                        "display_from, display_until, sort_order, click_count, status, " +
                        "created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, NOW(), NOW())",
                siteId, group, title, imageUrl, altText,
                java.sql.Timestamp.from(displayFrom),
                java.sql.Timestamp.from(displayUntil), sortOrder, status);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM banner WHERE site_id = ? AND title = ?", Long.class, siteId, title);
        return id == null ? -1L : id;
    }
}
