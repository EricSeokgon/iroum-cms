package kr.co.ircp.cms.domain.content.i18n.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nResourceItem;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nResponse;
import kr.co.ircp.cms.domain.content.i18n.dto.I18nUpsertRequest;
import kr.co.ircp.cms.domain.content.i18n.service.I18nResolver;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * I18nController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-010-D: 다국어 리소스 API HTTP 계층 검증.
 */
@WebMvcTest(I18nController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("I18nController GREEN 테스트 (REQ-CONTENT-010-D)")
class I18nControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private I18nResolver i18nResolver;

    @Test
    @WithMockUser(authorities = {"CONTENT:READ"})
    @DisplayName("GET /i18n — 리소스 조회 200 OK + 필드 맵 응답")
    void getI18nFields_returnsOk() throws Exception {
        I18nResponse response = new I18nResponse(
                "page", 1L, "ko", Map.of("title", "제목", "body", "본문")
        );
        when(i18nResolver.resolveFields(eq("page"), eq(1L), eq("ko"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/content/i18n")
                        .param("namespace", "page")
                        .param("resourceId", "1")
                        .param("lang", "ko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.namespace").value("page"))
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.fields.title").value("제목"))
                .andExpect(jsonPath("$.fields.body").value("본문"));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:READ"})
    @DisplayName("GET /i18n — lang 미지정 시 ko 기본값 사용 200 OK")
    void getI18nFields_defaultLang_returnsOk() throws Exception {
        I18nResponse response = new I18nResponse(
                "page", 1L, "ko", Map.of("title", "기본")
        );
        when(i18nResolver.resolveFields(eq("page"), eq(1L), eq("ko"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/content/i18n")
                        .param("namespace", "page")
                        .param("resourceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("ko"));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("PUT /i18n — bulk upsert 200 OK")
    void bulkUpsert_returnsOk() throws Exception {
        I18nUpsertRequest req = new I18nUpsertRequest(List.of(
                new I18nResourceItem("page", 1L, "ko", "title", "한글 제목"),
                new I18nResourceItem("page", 1L, "en", "title", "English Title")
        ));
        doNothing().when(i18nResolver).bulkUpsert(anyList());

        mockMvc.perform(put("/api/v1/content/i18n")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("PUT /i18n — items 누락 시 400 Bad Request")
    void bulkUpsert_missingItems_returns400() throws Exception {
        // items가 NotNull이므로 누락 시 400
        String invalidJson = "{}";

        mockMvc.perform(put("/api/v1/content/i18n")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /i18n — 인증 없이 접근 시 403 Forbidden")
    void bulkUpsert_unauthenticated_returns403() throws Exception {
        I18nUpsertRequest req = new I18nUpsertRequest(List.of(
                new I18nResourceItem("page", 1L, "ko", "title", "한글 제목")
        ));

        mockMvc.perform(put("/api/v1/content/i18n")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
