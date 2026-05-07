package kr.co.ircp.cms.domain.content.banner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.banner.dto.BannerRequest;
import kr.co.ircp.cms.domain.content.banner.dto.BannerResponse;
import kr.co.ircp.cms.domain.content.banner.service.BannerService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BannerController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-009-D: 배너 CRUD + 클릭 이벤트 HTTP 계층 검증.
 */
@WebMvcTest(BannerController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("BannerController GREEN 테스트 (REQ-CONTENT-009-D)")
class BannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BannerService bannerService;

    private static BannerResponse sampleBanner(Long id, String group) {
        return new BannerResponse(
                id, 1L, group, "배너 제목", "https://img/x.png", "https://link",
                "_blank", "대체 텍스트",
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(7, ChronoUnit.DAYS),
                1, 0L, "ACTIVE",
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /banners — 그룹별 활성 배너 200 OK (PUBLIC)")
    void getActiveBanners_returnsOk() throws Exception {
        when(bannerService.getActiveBannersByGroup(eq("MAIN_TOP")))
                .thenReturn(List.of(sampleBanner(1L, "MAIN_TOP"), sampleBanner(2L, "MAIN_TOP")));

        mockMvc.perform(get("/api/v1/content/banners").param("group", "MAIN_TOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bannerGroupCode").value("MAIN_TOP"));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("POST /banners — 등록 200 OK")
    void registerBanner_returnsOk() throws Exception {
        BannerRequest req = new BannerRequest(
                1L, "MAIN_TOP", "배너", "https://img/x.png", null, "_self",
                "alt", Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS), 1
        );
        when(bannerService.registerBanner(any(BannerRequest.class))).thenReturn(sampleBanner(10L, "MAIN_TOP"));

        mockMvc.perform(post("/api/v1/content/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("POST /banners — altText 누락 시 400 Bad Request")
    void registerBanner_missingAltText_returns400() throws Exception {
        // altText가 NotBlank이므로 누락 시 400
        String invalidJson = "{\"siteId\":1,\"bannerGroupCode\":\"X\",\"title\":\"t\",\"imageUrl\":\"u\",\"displayFrom\":\"2026-01-01T00:00:00Z\",\"displayUntil\":\"2026-01-02T00:00:00Z\"}";

        mockMvc.perform(post("/api/v1/content/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("PUT /banners/{id} — 수정 200 OK")
    void updateBanner_returnsOk() throws Exception {
        BannerRequest req = new BannerRequest(
                1L, "MAIN_TOP", "변경", "https://img/y.png", null, "_self",
                "alt", Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS), 2
        );
        when(bannerService.updateBanner(eq(5L), any(BannerRequest.class)))
                .thenReturn(sampleBanner(5L, "MAIN_TOP"));

        mockMvc.perform(put("/api/v1/content/banners/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("DELETE /banners/{id} — 삭제 204 No Content")
    void deleteBanner_returnsNoContent() throws Exception {
        doNothing().when(bannerService).deleteBanner(eq(5L));

        mockMvc.perform(delete("/api/v1/content/banners/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /banners/{id}/click — 클릭 기록 204 No Content (PUBLIC)")
    void recordClick_returnsNoContent() throws Exception {
        doNothing().when(bannerService).recordClick(eq(5L));

        mockMvc.perform(post("/api/v1/content/banners/5/click"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /banners — 인증 없이 접근 시 403 Forbidden")
    void registerBanner_unauthenticated_returns403() throws Exception {
        BannerRequest req = new BannerRequest(
                1L, "MAIN_TOP", "배너", "https://img/x.png", null, "_self",
                "alt", Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.DAYS), 1
        );
        mockMvc.perform(post("/api/v1/content/banners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
