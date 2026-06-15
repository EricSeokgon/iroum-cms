package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryDetail;
import kr.co.ircp.cms.domain.board.dto.PostHistoryItem;
import kr.co.ircp.cms.domain.board.dto.PostSummary;
import kr.co.ircp.cms.domain.board.service.PostHistoryService;
import kr.co.ircp.cms.domain.board.service.PostService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private PostHistoryService postHistoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/board/posts?bbsId=X — 200 OK, 페이징 응답")
    void listPosts_returns200WithPage() throws Exception {
        PostSummary summary = new PostSummary(
                1L, 1L, "NOTICE", "공지 제목", 10L, "관리자",
                false, false, 0L, 0L, 0L, Instant.now(), "ko", List.of()
        );
        PageResponse<PostSummary> page = PageResponse.of(List.of(summary), 0, 20, 1L);
        when(postService.listPosts(anyLong(), anyInt(), anyInt(), anyString())).thenReturn(page);

        // PostController는 /api/v1/board/posts 매핑이며 bbsId를 쿼리 파라미터로 받음
        mockMvc.perform(get("/api/v1/board/posts")
                        .param("bbsId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/board/posts/search?bbsId=X — 200 OK, 검색 결과")
    void searchPosts_returns200WithResults() throws Exception {
        PageResponse<PostSummary> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(postService.searchPosts(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/board/posts/search")
                        .param("bbsId", "1")
                        .param("keyword", "공지"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(authorities = {"CONTENT:WRITE"})
    @DisplayName("POST /api/v1/board/posts — 201 Created, 게시글 반환")
    void createPost_returns201WithDetail() throws Exception {
        PostDetail created = new PostDetail(
                5L, 1L, "NOTICE", false, "테스트 제목", "<p>내용</p>",
                null, null, false, null, null, false,
                0L, 0L, "ACTIVE", null, List.of(), List.of(),
                Instant.now(), Instant.now()
        );
        when(postService.createPost(any(), isNull())).thenReturn(created);

        PostCreateRequest request = new PostCreateRequest(
                1L, "테스트 제목", "<p>내용</p>", "내용",
                false, null, null, false, null, null, null, null
        );

        // bbsMasterId(=bbsId)는 요청 바디에 포함되어 전달됨
        mockMvc.perform(post("/api/v1/board/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/v1/board/posts/{id} — expectedVersion 누락 시 400 Bad Request")
    void updatePost_missingExpectedVersion_returns400() throws Exception {
        // SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005: expectedVersion 은 @NotNull → 누락 시 400
        String bodyWithoutVersion =
                "{\"title\":\"수정 제목\",\"contentHtml\":\"<p>내용</p>\",\"isNotice\":false,\"isSecret\":false}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/board/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutVersion))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-POST-SCHEDULE-001 — 예약 발행 API
    // ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("POST /{postId}/schedule — 200 OK, status=SCHEDULED 반환 (AC-PS-001)")
    void schedulePost_returns200WithScheduled() throws Exception {
        PostDetail scheduled = new PostDetail(
                1L, 1L, "NOTICE", false, "제목", "<p>내용</p>",
                null, null, false, null, null, false,
                0L, 0L, "SCHEDULED", null, List.of(), List.of(),
                Instant.now(), Instant.now()
        );
        when(postService.schedulePost(anyLong(), any())).thenReturn(scheduled);

        String body = objectMapper.writeValueAsString(
                new kr.co.ircp.cms.domain.board.dto.PostScheduleRequest(
                        Instant.now().plusSeconds(3600)));

        mockMvc.perform(post("/api/v1/board/posts/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{postId}/schedule — 과거 시각은 @Future 위반으로 400 (AC-PS-002)")
    void schedulePost_pastTime_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new kr.co.ircp.cms.domain.board.dto.PostScheduleRequest(
                        Instant.now().minusSeconds(3600)));

        mockMvc.perform(post("/api/v1/board/posts/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /{postId}/schedule — scheduledAt 누락은 @NotNull 위반으로 400 (AC-PS-003)")
    void schedulePost_nullTime_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/board/posts/1/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /{postId}/schedule — 200 OK, status=DRAFT 반환 (AC-PS-006)")
    void cancelSchedule_returns200WithDraft() throws Exception {
        PostDetail draft = new PostDetail(
                1L, 1L, "NOTICE", false, "제목", "<p>내용</p>",
                null, null, false, null, null, false,
                0L, 0L, "DRAFT", null, List.of(), List.of(),
                Instant.now(), Instant.now()
        );
        when(postService.cancelSchedule(anyLong())).thenReturn(draft);

        mockMvc.perform(delete("/api/v1/board/posts/1/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /{postId}/schedule — 비SCHEDULED 취소는 409 (AC-PS-007)")
    void cancelSchedule_notScheduled_returns409() throws Exception {
        when(postService.cancelSchedule(anyLong()))
                .thenThrow(new kr.co.ircp.cms.domain.board.exception.PostScheduleConflictException(
                        "예약 상태가 아닙니다."));

        mockMvc.perform(delete("/api/v1/board/posts/1/schedule"))
                .andExpect(status().isConflict());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-POST-HISTORY-001 — 게시글 버전 히스토리 조회 API
    // ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /{postId}/history — 200 OK, version DESC 페이징 목록 (AC-PH-001/002)")
    void getPostHistory_returns200WithPage() throws Exception {
        PostHistoryItem item = new PostHistoryItem(
                20L, 2, "관리자", "오타 수정", Instant.now());
        PageResponse<PostHistoryItem> page = PageResponse.of(List.of(item), 0, 20, 1L);
        when(postHistoryService.getHistory(anyLong(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/board/posts/7/history")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].version").value(2))
                .andExpect(jsonPath("$.content[0].editorName").value("관리자"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{postId}/history — 이력 없으면 200 OK + 빈 목록 (AC-PH-006)")
    void getPostHistory_noHistory_returns200Empty() throws Exception {
        PageResponse<PostHistoryItem> empty = PageResponse.of(List.of(), 0, 20, 0L);
        when(postHistoryService.getHistory(anyLong(), anyInt(), anyInt())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/board/posts/99/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{postId}/history/{version} — 200 OK, title+contentHtml 본문 (AC-PH-004)")
    void getPostVersion_returns200WithContent() throws Exception {
        PostHistoryDetail detail = new PostHistoryDetail(
                10L, 1, "관리자", "최초 작성", Instant.now(),
                "옛 제목", "<p>옛 본문</p>");
        when(postHistoryService.getVersion(anyLong(), anyInt())).thenReturn(detail);

        mockMvc.perform(get("/api/v1/board/posts/7/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("옛 제목"))
                .andExpect(jsonPath("$.contentHtml").value("<p>옛 본문</p>"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{postId}/history/{version} — 미존재 버전은 404 (AC-PH-005)")
    void getPostVersion_unknown_returns404() throws Exception {
        when(postHistoryService.getVersion(anyLong(), anyInt()))
                .thenThrow(new kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException(7L, 999));

        mockMvc.perform(get("/api/v1/board/posts/7/history/999"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-CONTENT-REVISION-001 M2 — 두 version diff API (REQ-REV-003)
    // ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /{postId}/history/diff?from&to — 200 OK, title·content diff 리스트 (AC-003-1/2)")
    void getPostHistoryDiff_returns200WithList() throws Exception {
        var titleDiff = new kr.co.ircp.cms.common.dto.RevisionDiffResponse(
                "title", 2, 3,
                List.of(new kr.co.ircp.cms.common.dto.DiffLine(
                        kr.co.ircp.cms.common.dto.DiffType.DELETE, 1, null, "옛 제목"),
                        new kr.co.ircp.cms.common.dto.DiffLine(
                                kr.co.ircp.cms.common.dto.DiffType.INSERT, null, 1, "새 제목")));
        var contentDiff = new kr.co.ircp.cms.common.dto.RevisionDiffResponse(
                "content", 2, 3,
                List.of(new kr.co.ircp.cms.common.dto.DiffLine(
                        kr.co.ircp.cms.common.dto.DiffType.EQUAL, 1, 1, "본문")));
        when(postHistoryService.diff(anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of(titleDiff, contentDiff));

        mockMvc.perform(get("/api/v1/board/posts/7/history/diff")
                        .param("from", "2")
                        .param("to", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].field").value("title"))
                .andExpect(jsonPath("$[0].fromVersion").value(2))
                .andExpect(jsonPath("$[0].toVersion").value(3))
                .andExpect(jsonPath("$[1].field").value("content"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /{postId}/history/diff — 미존재 version은 404 (AC-003)")
    void getPostHistoryDiff_unknownVersion_returns404() throws Exception {
        when(postHistoryService.diff(anyLong(), anyInt(), anyInt()))
                .thenThrow(new kr.co.ircp.cms.domain.board.exception.PostHistoryVersionNotFoundException(7L, 99));

        mockMvc.perform(get("/api/v1/board/posts/7/history/diff")
                        .param("from", "99")
                        .param("to", "3"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────
    // SPEC-CMS-SECURITY-CTRL-AUTHZ-COVERAGE-001 — 권한 거부 시나리오 (적용 불가)
    //
    // PostController는 클래스/메소드 레벨 @PreAuthorize 어노테이션이 없으며,
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
