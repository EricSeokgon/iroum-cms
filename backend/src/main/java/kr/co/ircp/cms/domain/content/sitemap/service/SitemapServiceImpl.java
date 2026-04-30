package kr.co.ircp.cms.domain.content.sitemap.service;

import kr.co.ircp.cms.domain.content.page.entity.Page;
import kr.co.ircp.cms.domain.content.page.mapper.PageMapper;
import kr.co.ircp.cms.domain.content.site.entity.Site;
import kr.co.ircp.cms.domain.content.site.mapper.SiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sitemap 생성 서비스 구현체.
 * REQ-CONTENT-007-D: PUBLISHED 페이지 기반 sitemap.xml 생성 + Caffeine 캐시 (TTL 1시간)
 *
 * // @MX:ANCHOR: [AUTO] SitemapServiceImpl — sitemap.xml 생성 전체 관리
 * // @MX:REASON: SitemapController, CacheEvict(sitemap)에서 fan_in >= 3으로 참조
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SitemapServiceImpl implements SitemapService {

    private static final DateTimeFormatter ISO_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private static final String SITEMAP_NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";

    private final PageMapper pageMapper;
    private final SiteMapper siteMapper;

    /**
     * PUBLISHED 페이지 기반 sitemap.xml 생성.
     * REQ-CONTENT-007-D: Content-Type: application/xml
     * REQ-CONTENT-007-D-3: Caffeine 캐시 (sitemap, TTL 1시간)
     */
    @Override
    @Cacheable(value = "sitemap", key = "#siteId")
    public String generate(Long siteId) {
        Site site = siteMapper.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. id=" + siteId));

        List<Page> pages = pageMapper.findPublishedBySiteId(siteId);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"").append(SITEMAP_NAMESPACE).append("\">\n");

        for (Page page : pages) {
            sb.append("  <url>\n");
            sb.append("    <loc>").append(site.getDomain()).append("/").append(page.getSlug()).append("</loc>\n");
            if (page.getUpdatedAt() != null) {
                sb.append("    <lastmod>").append(ISO_DATE_FORMATTER.format(page.getUpdatedAt())).append("</lastmod>\n");
            }
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.8</priority>\n");
            sb.append("  </url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }
}
