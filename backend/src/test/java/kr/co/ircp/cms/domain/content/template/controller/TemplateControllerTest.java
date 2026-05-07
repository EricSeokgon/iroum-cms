package kr.co.ircp.cms.domain.content.template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.template.dto.TemplateRequest;
import kr.co.ircp.cms.domain.content.template.dto.TemplateResponse;
import kr.co.ircp.cms.domain.content.template.service.TemplateService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TemplateController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-004-D: 템플릿 관리 HTTP 계층 검증.
 */
@WebMvcTest(TemplateController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("TemplateController GREEN 테스트 (REQ-CONTENT-004-D)")
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TemplateService templateService;

    private static TemplateResponse sampleTemplate(Long id, String code) {
        return new TemplateResponse(
                id, code, "템플릿-" + code, "FULL",
                "<html>{{CONTENT}}</html>", "[]", "[]", "설명", "ACTIVE",
                Instant.now(), Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"TEMPLATE:READ"})
    @DisplayName("GET /templates — 목록 조회 200 OK")
    void listTemplates_returnsOk() throws Exception {
        when(templateService.listTemplates())
                .thenReturn(List.of(sampleTemplate(1L, "T1"), sampleTemplate(2L, "T2")));

        mockMvc.perform(get("/api/v1/content/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("T1"));
    }

    @Test
    @WithMockUser(authorities = {"TEMPLATE:READ"})
    @DisplayName("GET /templates/{id} — 단건 조회 200 OK")
    void getTemplate_returnsOk() throws Exception {
        when(templateService.getTemplate(eq(1L))).thenReturn(sampleTemplate(1L, "T1"));

        mockMvc.perform(get("/api/v1/content/templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("T1"));
    }

    @Test
    @WithMockUser(authorities = {"TEMPLATE:WRITE"})
    @DisplayName("POST /templates — 등록 201 Created + Location")
    void createTemplate_returnsCreated() throws Exception {
        TemplateRequest req = new TemplateRequest(
                "T_NEW", "신규 템플릿", "FULL", "<html>{{CONTENT}}</html>",
                "[]", "[]", "설명"
        );
        when(templateService.createTemplate(any(TemplateRequest.class)))
                .thenReturn(sampleTemplate(10L, "T_NEW"));

        mockMvc.perform(post("/api/v1/content/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.code").value("T_NEW"));
    }

    @Test
    @WithMockUser(authorities = {"TEMPLATE:WRITE"})
    @DisplayName("POST /templates — layoutType 패턴 위반 시 400 Bad Request")
    void createTemplate_invalidLayout_returns400() throws Exception {
        // layoutType은 FULL|SIDEBAR_LEFT|SIDEBAR_RIGHT|LANDING|BLANK 만 허용
        String invalidJson = "{\"code\":\"T1\",\"name\":\"n\",\"layoutType\":\"INVALID\",\"htmlTemplate\":\"<html>{{CONTENT}}</html>\"}";

        mockMvc.perform(post("/api/v1/content/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"TEMPLATE:WRITE"})
    @DisplayName("PUT /templates/{id} — 수정 200 OK")
    void updateTemplate_returnsOk() throws Exception {
        TemplateRequest req = new TemplateRequest(
                "T1", "수정", "FULL", "<html>{{CONTENT}}</html>", "[]", "[]", "수정 설명"
        );
        when(templateService.updateTemplate(eq(5L), any(TemplateRequest.class)))
                .thenReturn(sampleTemplate(5L, "T1"));

        mockMvc.perform(put("/api/v1/content/templates/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(authorities = {"TEMPLATE:WRITE"})
    @DisplayName("PATCH /templates/{id}/status — 상태 변경 200 OK")
    void changeStatus_returnsOk() throws Exception {
        when(templateService.changeStatus(eq(5L), anyString()))
                .thenReturn(sampleTemplate(5L, "T1"));

        mockMvc.perform(patch("/api/v1/content/templates/5/status")
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("POST /templates — 인증 없이 접근 시 403 Forbidden")
    void createTemplate_unauthenticated_returns403() throws Exception {
        TemplateRequest req = new TemplateRequest(
                "T_NEW", "신규 템플릿", "FULL", "<html>{{CONTENT}}</html>",
                "[]", "[]", "설명"
        );

        mockMvc.perform(post("/api/v1/content/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
