package kr.co.ircp.cms.domain.content.page.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.content.page.dto.BlockOrderRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockRequest;
import kr.co.ircp.cms.domain.content.page.dto.ContentBlockResponse;
import kr.co.ircp.cms.domain.content.page.service.ContentBlockService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ContentBlockController GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-004 REQ-CONTENT-006-D: 콘텐츠 블록 CRUD + 순서 변경 HTTP 계층 검증.
 */
@WebMvcTest(ContentBlockController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("ContentBlockController GREEN 테스트 (REQ-CONTENT-006-D)")
class ContentBlockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentBlockService contentBlockService;

    private static ContentBlockResponse sampleBlock(Long id, int sortOrder) {
        return new ContentBlockResponse(
                id, 1L, "RICH_TEXT", sortOrder, "{\"text\":\"hello\"}", 1,
                Instant.now(), Instant.now()
        );
    }

    @Test
    @WithMockUser(authorities = {"PAGE:READ"})
    @DisplayName("GET /pages/{pageId}/blocks — 목록 조회 200 OK")
    void listBlocks_returnsOk() throws Exception {
        when(contentBlockService.listBlocks(eq(1L)))
                .thenReturn(List.of(sampleBlock(1L, 1), sampleBlock(2L, 2)));

        mockMvc.perform(get("/api/v1/content/pages/1/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].sortOrder").value(2));
    }

    @Test
    @WithMockUser(authorities = {"BLOCK:WRITE"})
    @DisplayName("POST /pages/{pageId}/blocks — 생성 201 Created")
    void createBlock_returnsCreated() throws Exception {
        ContentBlockRequest req = new ContentBlockRequest("RICH_TEXT", 1, "{\"text\":\"hello\"}");
        ContentBlockResponse created = sampleBlock(10L, 1);
        when(contentBlockService.createBlock(eq(1L), any(ContentBlockRequest.class), anySet()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/content/pages/1/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.blockType").value("RICH_TEXT"));
    }

    @Test
    @WithMockUser(authorities = {"BLOCK:WRITE"})
    @DisplayName("POST /pages/{pageId}/blocks — blockType 패턴 위반 시 400")
    void createBlock_invalidType_returns400() throws Exception {
        // blockType이 패턴 RICH_TEXT|IMAGE|HTML|MARKDOWN|EMBED 외 값
        String invalidJson = "{\"blockType\":\"INVALID\",\"sortOrder\":1,\"payload\":\"{}\"}";

        mockMvc.perform(post("/api/v1/content/pages/1/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"BLOCK:WRITE"})
    @DisplayName("PUT /pages/{pageId}/blocks/{blockId} — 수정 200 OK")
    void updateBlock_returnsOk() throws Exception {
        ContentBlockRequest req = new ContentBlockRequest("RICH_TEXT", 2, "{\"text\":\"updated\"}");
        ContentBlockResponse updated = sampleBlock(5L, 2);
        when(contentBlockService.updateBlock(eq(1L), eq(5L), any(ContentBlockRequest.class), anySet()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/content/pages/1/blocks/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.sortOrder").value(2));
    }

    @Test
    @WithMockUser(authorities = {"BLOCK:WRITE"})
    @DisplayName("DELETE /pages/{pageId}/blocks/{blockId} — 삭제 204 No Content")
    void deleteBlock_returnsNoContent() throws Exception {
        doNothing().when(contentBlockService).deleteBlock(eq(1L), eq(5L));

        mockMvc.perform(delete("/api/v1/content/pages/1/blocks/5"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = {"BLOCK:WRITE"})
    @DisplayName("PATCH /pages/{pageId}/blocks/order — 순서 일괄 갱신 204 No Content")
    void reorderBlocks_returnsNoContent() throws Exception {
        BlockOrderRequest req = new BlockOrderRequest(List.of(
                new BlockOrderRequest.BlockOrderItem(1L, 2),
                new BlockOrderRequest.BlockOrderItem(2L, 1)
        ));
        doNothing().when(contentBlockService).reorderBlocks(eq(1L), any(BlockOrderRequest.class));

        mockMvc.perform(patch("/api/v1/content/pages/1/blocks/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
