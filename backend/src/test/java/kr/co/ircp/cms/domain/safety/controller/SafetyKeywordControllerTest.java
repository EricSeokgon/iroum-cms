package kr.co.ircp.cms.domain.safety.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.safety.dto.KeywordRequest;
import kr.co.ircp.cms.domain.safety.dto.KeywordSummary;
import kr.co.ircp.cms.domain.safety.exception.SafetyKeywordNotFoundException;
import kr.co.ircp.cms.domain.safety.service.SafetyKeywordService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SafetyKeywordController @WebMvcTest (GREEN 단계).
 *
 * <p>REQ-SAFETY-002 — 키워드 사전 CRUD HTTP 계층 검증.
 */
@WebMvcTest(SafetyKeywordController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("SafetyKeywordController GREEN 테스트 (REQ-SAFETY-002)")
class SafetyKeywordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SafetyKeywordService keywordService;

    private static KeywordSummary sample(Long id, String code, String term, String category) {
        return new KeywordSummary(id, category, code, term, term + " 설명", "ACTIVE", List.of(term + "1", term + "2"));
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/keywords — 키워드 목록 200 OK")
    void list_returnsOk() throws Exception {
        when(keywordService.listKeywords(eq("INDUSTRY"))).thenReturn(List.of(
                sample(1L, "K_001", "건설", "INDUSTRY"),
                sample(2L, "K_002", "제조", "INDUSTRY")
        ));

        mockMvc.perform(get("/api/v1/safety/admin/keywords")
                        .param("category", "INDUSTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("K_001"))
                .andExpect(jsonPath("$[0].term").value("건설"))
                .andExpect(jsonPath("$[0].synonyms").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/safety/admin/keywords — 카테고리 필터 미지정 시에도 200 OK")
    void list_withoutCategoryFilter_returnsOk() throws Exception {
        when(keywordService.listKeywords(eq(null))).thenReturn(List.of(
                sample(1L, "K_001", "건설", "INDUSTRY")
        ));

        mockMvc.perform(get("/api/v1/safety/admin/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/keywords — 키워드 생성 201 Created")
    void create_returnsCreated() throws Exception {
        KeywordRequest request = new KeywordRequest(
                "PROCESS", "K_100", "용접", "용접 공정", List.of("용접작업")
        );
        KeywordSummary created = new KeywordSummary(
                100L, "PROCESS", "K_100", "용접", "용접 공정", "ACTIVE", List.of("용접작업")
        );
        when(keywordService.createKeyword(any(KeywordRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/safety/admin/keywords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.code").value("K_100"))
                .andExpect(jsonPath("$.term").value("용접"));
    }

    @Test
    @DisplayName("POST /api/v1/safety/admin/keywords — 필수 필드 누락 시 400")
    void create_missingRequired_returns400() throws Exception {
        // category, code, term 모두 @NotBlank
        String invalidJson = "{\"description\":\"설명만\"}";

        mockMvc.perform(post("/api/v1/safety/admin/keywords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/safety/admin/keywords/{id} — 키워드 수정 200 OK")
    void update_returnsOk() throws Exception {
        KeywordRequest request = new KeywordRequest(
                "PROCESS", "K_100", "용접 (수정)", "수정된 설명", List.of("welding")
        );
        KeywordSummary updated = new KeywordSummary(
                100L, "PROCESS", "K_100", "용접 (수정)", "수정된 설명", "ACTIVE", List.of("welding")
        );
        when(keywordService.updateKeyword(eq(100L), any(KeywordRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/safety/admin/keywords/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.term").value("용접 (수정)"));
    }

    @Test
    @DisplayName("PUT /api/v1/safety/admin/keywords/{id} — 미존재 시 404")
    void update_notFound_returns404() throws Exception {
        KeywordRequest request = new KeywordRequest(
                "PROCESS", "K_999", "없음", "없음 설명", List.of()
        );
        when(keywordService.updateKeyword(eq(999L), any(KeywordRequest.class)))
                .thenThrow(new SafetyKeywordNotFoundException(999L));

        mockMvc.perform(put("/api/v1/safety/admin/keywords/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/safety/admin/keywords/{id} — 비활성화 204 No Content")
    void deactivate_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/safety/admin/keywords/5"))
                .andExpect(status().isNoContent());

        verify(keywordService).deactivateKeyword(5L);
    }

    @Test
    @DisplayName("DELETE /api/v1/safety/admin/keywords/{id} — 미존재 시 404")
    void deactivate_notFound_returns404() throws Exception {
        doThrow(new SafetyKeywordNotFoundException(404L))
                .when(keywordService).deactivateKeyword(404L);

        mockMvc.perform(delete("/api/v1/safety/admin/keywords/404"))
                .andExpect(status().isNotFound());
    }
}
