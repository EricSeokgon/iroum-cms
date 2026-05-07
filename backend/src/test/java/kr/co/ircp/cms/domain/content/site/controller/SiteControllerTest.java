package kr.co.ircp.cms.domain.content.site.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.site.dto.SiteResponse;
import kr.co.ircp.cms.domain.content.site.dto.SiteUpdateRequest;
import kr.co.ircp.cms.domain.content.site.service.SiteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SiteController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-003-D: 사이트 마스터 API HTTP 계층 검증.
 */
@WebMvcTest(SiteController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SiteController GREEN 테스트 (REQ-CONTENT-003-D)")
class SiteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SiteService siteService;

    private static SiteResponse sampleSite(Long id, String code) {
        return new SiteResponse(
                id, code, "사이트-" + code, "example.com", "ko", "ACTIVE",
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /sites/current — 현재 사이트 조회 200 OK")
    void getCurrentSite_returnsOk() throws Exception {
        when(siteService.getCurrentSite(anyString())).thenReturn(sampleSite(1L, "MAIN"));

        mockMvc.perform(get("/api/v1/content/sites/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("MAIN"));
    }

    @Test
    @WithMockUser(authorities = {"SITE:WRITE"})
    @DisplayName("PUT /sites/{id} — 수정 200 OK")
    void updateSite_returnsOk() throws Exception {
        SiteUpdateRequest req = new SiteUpdateRequest("새이름", "new.example.com", "ko", null);
        when(siteService.updateSite(eq(1L), any(SiteUpdateRequest.class)))
                .thenReturn(sampleSite(1L, "MAIN"));

        mockMvc.perform(put("/api/v1/content/sites/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(authorities = {"SYSTEM:ADMIN"})
    @DisplayName("POST /sites — 신규 사이트 생성 201 Created")
    void createSite_returnsCreated() throws Exception {
        SiteUpdateRequest req = new SiteUpdateRequest("신규", "new.example.com", "ko", null);
        when(siteService.createSite(any(SiteUpdateRequest.class)))
                .thenReturn(sampleSite(2L, "NEW"));

        mockMvc.perform(post("/api/v1/content/sites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("NEW"));
    }

    @Test
    @WithMockUser(authorities = {"SITE:WRITE"})
    @DisplayName("PUT /sites/{id} — name 누락 시 400 Bad Request")
    void updateSite_missingName_returns400() throws Exception {
        // name이 NotBlank이므로 누락 시 400
        String invalidJson = "{\"domain\":\"example.com\",\"defaultLanguage\":\"ko\"}";

        mockMvc.perform(put("/api/v1/content/sites/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /sites/{id} — 인증 없이 접근 시 403 Forbidden")
    void updateSite_unauthenticated_returns403() throws Exception {
        SiteUpdateRequest req = new SiteUpdateRequest("새이름", "new.example.com", "ko", null);

        mockMvc.perform(put("/api/v1/content/sites/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
