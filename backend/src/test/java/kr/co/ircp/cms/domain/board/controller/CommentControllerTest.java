package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.service.CommentService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CommentController GREEN 단계 테스트.
 * REQ-BOARD-003: 댓글 CRUD API HTTP 계층 검증.
 */
@WebMvcTest(CommentController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("CommentController GREEN 테스트 (REQ-BOARD-003)")
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/boards/{bbsMasterId}/posts/{postId}/comments — 200 OK, 목록 반환")
    void listComments_returns200WithList() throws Exception {
        CommentSummary comment = new CommentSummary(
                1L, 1L, null, 10L, "작성자", null,
                "댓글 내용입니다.", "ACTIVE", List.of(),
                Instant.now(), Instant.now()
        );
        when(commentService.listComments(anyLong())).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/v1/boards/1/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("댓글 내용입니다."));
    }

    @Test
    @DisplayName("POST /api/v1/boards/{bbsMasterId}/posts/{postId}/comments — 201 Created, 댓글 반환")
    void createComment_returns201WithBody() throws Exception {
        CommentSummary created = new CommentSummary(
                7L, 1L, null, null, null, "익명",
                "댓글 내용입니다.", "ACTIVE", List.of(),
                Instant.now(), Instant.now()
        );
        when(commentService.createComment(anyLong(), any(), any())).thenReturn(created);

        CommentCreateRequest request = new CommentCreateRequest(
                null, "댓글 내용입니다.", null, null, null
        );

        mockMvc.perform(post("/api/v1/boards/1/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("DELETE /api/v1/boards/{bbsMasterId}/posts/{postId}/comments/{commentId} — 204 No Content")
    void deleteComment_returns204() throws Exception {
        doNothing().when(commentService).deleteComment(anyLong(), any());

        mockMvc.perform(delete("/api/v1/boards/1/posts/1/comments/1"))
                .andExpect(status().isNoContent());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // CommentController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
    // 운영 환경에서는 SecurityConfig의 HTTP 레벨 정책(.anyRequest().authenticated())로
    // /api/v1/boards/** 경로 인증만 강제된다. 권한(role/authority)별 차등 통제는 없다.
    //
    // 본 슬라이스 테스트는 SecurityAutoConfiguration을 제외하므로 HTTP 레벨 정책이 미적용되며,
    // 메소드 레벨 정책 거부 트리거가 없어 ExceptionTranslationFilter가 EntryPoint를 호출하지 않는다.
    // 따라서 슬라이스에서 401(미인증) / 403(권한 부족) 응답을 결정적으로 검증할 수 없다.
    //
    // 401(미인증) / 403(권한 부족) 회귀는 SPEC-CMS-SECURITY-AUTHZ-MATRIX-001
    // (HTTP 매트릭스 IT 레이어, @SpringBootTest)에서 검증한다.
    // ──────────────────────────────────────────────────────────────
}
