package kr.co.ircp.cms.domain.content.sitemap.service;

/**
 * Sitemap 생성 서비스 인터페이스.
 * REQ-CONTENT-007-D: sitemap.xml 자동 생성
 *
 * // @MX:ANCHOR: [AUTO] SitemapService — sitemap.xml 생성 계약
 * // @MX:REASON: SitemapController, CacheEvict(sitemap)에서 fan_in >= 3으로 참조
 */
public interface SitemapService {

    /**
     * PUBLISHED 페이지 기반 sitemap.xml 문자열 생성.
     * REQ-CONTENT-007-D: Content-Type: application/xml
     */
    String generate(Long siteId);
}
