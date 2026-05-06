package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PostController GREEN 단계 테스트.
 * REQ-BOARD-002: 게시글 CRUD + 검색 API HTTP 계층 검증.
 */
@WebMvcTest(PostController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("PostController GREEN 테스트 (REQ-BOARD-002)")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts — 200 OK, 페이징 응답")
    void listPosts_returns200WithPage() throws Exception {
        PostSummary summary = new PostSummary(
                1L, 1L, "NOTICE", "공지 제목", 10L, "관리자",
                false, false, 0L, 0L, 0L, Instant.now()
        );
        PageResponse<PostSummary> page = PageResponse.of(List.of(summary), 0, 20, 1L);
        when(postService.listPosts(anyLong(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/boards/1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/search — 200 OK, 검색 결과")
    void searchPosts_returns200WithResults() throws Exception {
        PageResponse<PostSummary> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(postService.searchPosts(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/boards/1/posts/search")
                        .param("keyword", "공지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/boards/{bbsMasterId}/posts — 201 Created, 게시글 반환")
    void createPost_returns201WithDetail() throws Exception {
        PostDetail created = new PostDetail(
                5L, 1L, "NOTICE", "테스트 제목", "<p>내용</p>",
                null, null, false, null, null, false,
                0L, 0L, "ACTIVE", null, List.of(),
                Instant.now(), Instant.now()
        );
        when(postService.createPost(any(), isNull())).thenReturn(created);

        PostCreateRequest request = new PostCreateRequest(
                1L, "테스트 제목", "<p>내용</p>", "내용",
                false, null, null, false, null, null, null
        );

        mockMvc.perform(post("/api/v1/boards/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }
}
