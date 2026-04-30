package kr.co.ircp.cms.domain.content.sitemap.controller;

import kr.co.ircp.cms.domain.content.sitemap.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Sitemap 컨트롤러.
 * REQ-CONTENT-007-D: GET /sitemap.xml — PUBLIC, Cache-Control: public, max-age=3600
 */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final SitemapService sitemapService;

    /**
     * sitemap.xml 반환.
     * REQ-CONTENT-007-D: Content-Type: application/xml, Cache-Control: public, max-age=3600
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getSitemap(@RequestParam(defaultValue = "1") Long siteId) {
        String xml = sitemapService.generate(siteId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }
}
