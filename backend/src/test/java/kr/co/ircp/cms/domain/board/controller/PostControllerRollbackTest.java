package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.service.PostHistoryService;
import kr.co.ircp.cms.domain.board.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostController 롤백 API GREEN 테스트.
 * SPEC-CMS-CONTENT-REVISION-001 M3 (REQ-REV-007) — POST /{postId}/history/{version}/rollback.
 */
@WebMvcTest(PostController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PostController 롤백 테스트 (SPEC-CMS-CONTENT-REVISION-001 M3)")
class PostControllerRollbackTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private PostHistoryService postHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    @DisplayName("POST /{postId}/history/{version}/rollback — 유효 expectedVersion 시 200 OK")
    void rollback_validRequest_returns200() throws Exception {
        PostHistoryDetail restored = new PostHistoryDetail(
                100L, 6, null, "ROLLBACK_FROM_v2", Instant.now(), "v2 제목", "<p>v2 본문</p>");
        when(postHistoryService.rollback(anyLong(), anyInt(), anyInt())).thenReturn(restored);

        mockMvc.perform(post("/api/v1/board/posts/7/history/2/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.title").value("v2 제목"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{postId}/history/{version}/rollback — expectedVersion 누락 시 400")
    void rollback_missingExpectedVersion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/board/posts/7/history/2/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{postId}/history/{version}/rollback — 미존재 버전은 404")
    void rollback_unknownVersion_returns404() throws Exception {
        when(postHistoryService.rollback(anyLong(), anyInt(), anyInt()))
                .thenThrow(new kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException(7L, 999));

        mockMvc.perform(post("/api/v1/board/posts/7/history/999/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":5}"))
                .andExpect(status().isNotFound());
    }
}
