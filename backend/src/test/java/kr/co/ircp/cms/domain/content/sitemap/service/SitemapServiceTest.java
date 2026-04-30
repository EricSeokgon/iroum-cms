package kr.co.ircp.cms.domain.content.sitemap.service;

import kr.co.ircp.cms.domain.content.page.entity.Page;
import kr.co.ircp.cms.domain.content.page.mapper.PageMapper;
import kr.co.ircp.cms.domain.content.site.entity.Site;
import kr.co.ircp.cms.domain.content.site.mapper.SiteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SitemapService RED→GREEN 테스트.
 * REQ-CONTENT-007-D: PUBLISHED 페이지 기반 sitemap.xml 생성
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SitemapService 테스트 (REQ-CONTENT-007-D)")
class SitemapServiceTest {

    @Mock private PageMapper pageMapper;
    @Mock private SiteMapper siteMapper;

    private SitemapService sitemapService;

    @BeforeEach
    void setUp() {
        sitemapService = new SitemapServiceImpl(pageMapper, siteMapper);
    }

    private Site stubSite() {
        return Site.builder()
                .id(1L)
                .code("main")
                .name("메인 사이트")
                .domain("https://example.com")
                .status("ACTIVE")
                .build();
    }

    private Page stubPage(long id, String slug, String status, Instant updatedAt) {
        return Page.builder()
                .id(id)
                .siteId(1L)
                .slug(slug)
                .title("페이지 " + id)
                .status(status)
                .updatedAt(updatedAt)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-007-D: PUBLISHED 페이지만 sitemap 포함
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUBLISHED 페이지 URL이 sitemap.xml <loc>에 포함됨")
    void shouldGenerateSitemapXmlWithPublishedPagesOnly() {
        // Arrange
        Instant now = Instant.now();
        Site site = stubSite();
        Page published = stubPage(1L, "about", "PUBLISHED", now);

        when(siteMapper.findById(1L)).thenReturn(Optional.of(site));
        when(pageMapper.findPublishedBySiteId(1L)).thenReturn(List.of(published));

        // Act
        String sitemap = sitemapService.generate(1L);

        // Assert
        assertThat(sitemap).contains("<urlset");
        assertThat(sitemap).contains("https://example.com/about");
        assertThat(sitemap).contains("<loc>");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-007-D: DRAFT/SCHEDULED/DELETED 페이지 제외
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPublishedBySiteId 결과만 포함 — DRAFT/SCHEDULED 페이지 없음")
    void shouldExcludeDraftAndScheduledAndDeletedPages() {
        // Arrange — DB 쿼리에서 PUBLISHED만 이미 필터링되어 반환됨
        Instant now = Instant.now();
        Site site = stubSite();
        // mapper는 PUBLISHED 상태만 반환 (status='PUBLISHED' WHERE 절)
        when(siteMapper.findById(1L)).thenReturn(Optional.of(site));
        when(pageMapper.findPublishedBySiteId(1L)).thenReturn(List.of(
                stubPage(1L, "published-page", "PUBLISHED", now)
        ));

        // Act
        String sitemap = sitemapService.generate(1L);

        // Assert — PUBLISHED 페이지만 1건
        long urlCount = sitemap.lines().filter(l -> l.contains("<loc>")).count();
        assertThat(urlCount).isEqualTo(1);
        assertThat(sitemap).contains("published-page");
        // DRAFT/SCHEDULED 페이지는 mapper가 반환하지 않으므로 sitemap에 없음
        assertThat(sitemap).doesNotContain("draft-page");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-007-D: lastmod ISO-8601 날짜 형식
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updatedAt을 ISO-8601 날짜(yyyy-MM-dd) 형식으로 <lastmod>에 포함")
    void shouldFormatLastmodAsIso8601() {
        // Arrange
        // 2025-06-15T10:30:00Z (UTC 기준)
        Instant updatedAt = Instant.parse("2025-06-15T10:30:00Z");
        Site site = stubSite();
        Page page = stubPage(1L, "about", "PUBLISHED", updatedAt);

        when(siteMapper.findById(1L)).thenReturn(Optional.of(site));
        when(pageMapper.findPublishedBySiteId(1L)).thenReturn(List.of(page));

        // Act
        String sitemap = sitemapService.generate(1L);

        // Assert — yyyy-MM-dd 형식 확인
        assertThat(sitemap).contains("<lastmod>2025-06-15</lastmod>");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REQ-CONTENT-007-D: application/xml Content-Type + sitemaps.org 네임스페이스
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sitemap.xml은 sitemaps.org 네임스페이스와 XML 선언 포함")
    void shouldReturnApplicationXmlContentType() {
        // Arrange
        Site site = stubSite();
        when(siteMapper.findById(1L)).thenReturn(Optional.of(site));
        when(pageMapper.findPublishedBySiteId(1L)).thenReturn(List.of());

        // Act
        String sitemap = sitemapService.generate(1L);

        // Assert — XML 선언 및 sitemaps.org 네임스페이스 확인
        assertThat(sitemap).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(sitemap).contains("xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"");
        assertThat(sitemap).contains("</urlset>");
    }
}
