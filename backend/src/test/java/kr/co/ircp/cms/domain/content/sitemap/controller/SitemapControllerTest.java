package kr.co.ircp.cms.domain.content.sitemap.controller;

import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.sitemap.service.SitemapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SitemapController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-007-D: sitemap.xml 자동 생성 HTTP 계층 검증.
 */
@WebMvcTest(SitemapController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SitemapController GREEN 테스트 (REQ-CONTENT-007-D)")
class SitemapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SitemapService sitemapService;

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
              <url>
                <loc>https://example.com/about</loc>
                <lastmod>2026-04-01</lastmod>
              </url>
            </urlset>
            """;

    @Test
    @DisplayName("GET /sitemap.xml — XML 응답 200 OK + Content-Type: application/xml")
    void getSitemap_returnsXml() throws Exception {
        when(sitemapService.generate(eq(1L))).thenReturn(SAMPLE_XML);

        mockMvc.perform(get("/sitemap.xml").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<urlset")))
                .andExpect(content().string(containsString("https://example.com/about")));
    }

    @Test
    @DisplayName("GET /sitemap.xml — Cache-Control: public, max-age=3600 헤더 포함")
    void getSitemap_returnsCacheControlHeader() throws Exception {
        when(sitemapService.generate(eq(1L))).thenReturn(SAMPLE_XML);

        mockMvc.perform(get("/sitemap.xml").param("siteId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", containsString("public")));
    }

    @Test
    @DisplayName("GET /sitemap.xml — siteId 파라미터 미지정 시 default(1) 사용 200 OK")
    void getSitemap_defaultsSiteIdToOne() throws Exception {
        when(sitemapService.generate(eq(1L))).thenReturn(SAMPLE_XML);

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML));
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — AC-COV-001-1/2 적용 불가
    // ──────────────────────────────────────────────────────────────
    // SitemapController는 메소드/클래스 레벨 @PreAuthorize 정책을 보유하지 않으며,
    // REQ-CONTENT-007-D 사양상 PUBLIC 엔드포인트(GET /sitemap.xml)로 운영된다.
    // 운영 SecurityConfig는 /api/v1/** 경로에 대해서만 .anyRequest().authenticated()
    // HTTP-level 정책을 강제하며, /sitemap.xml은 PUBLIC 사양에 따라 익명 접근이
    // 허용되어야 한다. @WebMvcTest 슬라이스에서는 401/403 변별 검증 트리거
    // (@PreAuthorize 권한 거부)가 발생하지 않으므로 본 SPEC AC-COV-001
    // 시나리오는 적용 불가.
    //
    // 검증 책임: SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 IT 레이어
    //   (@SpringBootTest + 운영 SecurityFilterChain + JwtAuthenticationFilter)
    //   가 PUBLIC 경로 익명 200 OK 응답을 회귀 검출한다.
}
