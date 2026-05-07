package kr.co.ircp.cms.domain.content.seo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectRequest;
import kr.co.ircp.cms.domain.content.seo.dto.SeoRedirectResponse;
import kr.co.ircp.cms.domain.content.seo.service.SeoRedirectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SeoRedirectController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-005-D-8: URL 리다이렉트 관리 HTTP 계층 검증.
 */
@WebMvcTest(SeoRedirectController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SeoRedirectController GREEN 테스트 (REQ-CONTENT-005-D-8)")
class SeoRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SeoRedirectService seoRedirectService;

    private static SeoRedirectResponse sampleRedirect(Long id, String from, String to) {
        return new SeoRedirectResponse(
                id, from, to, (short) 301, true, "이전 → 신규 경로", Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:READ"})
    @DisplayName("GET /seo/redirects — 목록 조회 200 OK")
    void getAllRedirects_returnsOk() throws Exception {
        when(seoRedirectService.getAllRedirects())
                .thenReturn(List.of(
                        sampleRedirect(1L, "/old", "/new"),
                        sampleRedirect(2L, "/legacy", "/modern")
                ));

        mockMvc.perform(get("/api/v1/content/seo/redirects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromPath").value("/old"))
                .andExpect(jsonPath("$[1].toPath").value("/modern"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:ADMIN"})
    @DisplayName("POST /seo/redirects — 생성 200 OK")
    void createRedirect_returnsOk() throws Exception {
        SeoRedirectRequest req = new SeoRedirectRequest("/old-path", "/new-path", (short) 301, "테스트");
        when(seoRedirectService.createRedirect(any(SeoRedirectRequest.class)))
                .thenReturn(sampleRedirect(10L, "/old-path", "/new-path"));

        mockMvc.perform(post("/api/v1/content/seo/redirects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.fromPath").value("/old-path"));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:ADMIN"})
    @DisplayName("POST /seo/redirects — fromPath 누락 시 400 Bad Request")
    void createRedirect_missingFromPath_returns400() throws Exception {
        // fromPath가 NotBlank이므로 누락 시 400
        String invalidJson = "{\"toPath\":\"/new\"}";

        mockMvc.perform(post("/api/v1/content/seo/redirects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:ADMIN"})
    @DisplayName("DELETE /seo/redirects/{id} — 삭제 204 No Content")
    void deleteRedirect_returnsNoContent() throws Exception {
        doNothing().when(seoRedirectService).deleteRedirect(eq(5L));

        mockMvc.perform(delete("/api/v1/content/seo/redirects/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /seo/redirects/{id} — 인증 없이 접근 시 403 Forbidden")
    void deleteRedirect_unauthenticated_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/content/seo/redirects/5"))
                .andExpect(status().isForbidden());
    }
}
