package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.service.PostService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostController RED 단계 테스트.
 * REQ-BOARD-002: 게시글 CRUD + 검색 API HTTP 계층 검증.
 *
 * <p>Step 2 GREEN 전까지 서비스가 UnsupportedOperationException을 던지므로
 * 모든 요청은 500 상태로 응답한다.
 */
@WebMvcTest(PostController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("PostController RED 테스트 (REQ-BOARD-002)")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts — 서비스 스텁 500 (RED)")
    void listPosts_serviceStub_returns500() throws Exception {
        when(postService.listPosts(anyLong(), anyInt(), anyInt()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards/1/posts"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/search — 서비스 스텁 500 (RED)")
    void searchPosts_serviceStub_returns500() throws Exception {
        when(postService.searchPosts(anyLong(), anyString(), anyInt(), anyInt()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        mockMvc.perform(get("/api/v1/boards/1/posts/search")
                        .param("keyword", "공지"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("POST /api/v1/boards/{bbsMasterId}/posts — 서비스 스텁 500 (RED)")
    void createPost_serviceStub_returns500() throws Exception {
        when(postService.createPost(any(), isNull()))
                .thenThrow(new UnsupportedOperationException("Step 2 GREEN 대기"));

        PostCreateRequest request = new PostCreateRequest(
                1L, "테스트 제목", "<p>내용</p>", "내용",
                false, null, null, false, null, null, null
        );

        mockMvc.perform(post("/api/v1/boards/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }
}
