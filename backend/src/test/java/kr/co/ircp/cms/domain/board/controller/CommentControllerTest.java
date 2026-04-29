package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.service.CommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CommentController RED 단계 테스트.
 * REQ-BOARD-003: 댓글 CRUD API HTTP 계층 검증.
 *
 * <p>Step 2 GREEN 전까지 서비스가 UnsupportedOperationException을 던지므로
 * 모든 요청은 500 상태로 응답한다.
 */
@WebMvcTest(CommentController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("CommentController RED 테스트 (REQ-BOARD-003)")
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/{postId}/comments — 서비스 스텁 500 (RED)")
    void listComments_serviceStub_returns500() throws Exception {
        when(commentService.listComments(anyLong()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards/1/posts/1/comments"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /api/v1/boards/{bbsMasterId}/posts/{postId}/comments — 서비스 스텁 500 (RED)")
    void createComment_serviceStub_returns500() throws Exception {
        when(commentService.createComment(anyLong(), any(), any()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        CommentCreateRequest request = new CommentCreateRequest(
                null, "댓글 내용입니다.", null, null, null
        );

        mockMvc.perform(post("/api/v1/boards/1/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("DELETE /api/v1/boards/{bbsMasterId}/posts/{postId}/comments/{commentId} — 서비스 스텁 500 (RED)")
    void deleteComment_serviceStub_returns500() throws Exception {
        doThrow(new UnsupportedOperationException("Step 2 GREEN 대기"))
                .when(commentService).deleteComment(anyLong(), any());

        mockMvc.perform(delete("/api/v1/boards/1/posts/1/comments/1"))
                .andExpect(status().is5xxServerError());
    }
}
