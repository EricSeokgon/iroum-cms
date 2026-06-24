package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostDetail;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
import kr.co.ircp.cms.domain.board.util.HtmlSanitizer;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PostService GREEN 단계 테스트.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색 + 조회수 dedupe
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService GREEN 테스트 (REQ-BOARD-002)")
class PostServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsPostHistoryMapper bbsPostHistoryMapper;
    @Mock private BbsViewLogMapper bbsViewLogMapper;
    @Mock private kr.co.ircp.cms.domain.board.repository.BbsPostI18nMapper bbsPostI18nMapper;
    @Mock private UserPointService userPointService;

    private PostService postService;
    private final HtmlSanitizer htmlSanitizer = new HtmlSanitizer();

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                bbsMasterMapper, bbsPostMapper, bbsPostHistoryMapper, bbsViewLogMapper,
                bbsPostI18nMapper, htmlSanitizer,
                new AuthorizationGuard(), userPointService
        );
    }

    private BbsMaster stubMaster(long id) {
        return BbsMaster.builder().id(id).code("NOTICE").name("공지사항")
                .type("NOTICE").status("ACTIVE").useComment(true).build();
    }

    private BbsPost stubPost(long id, long bbsId) {
        // authorId=1L 고정: 기본 케이스의 update/delete 요청자(1L)와 일치하도록 설정.
        return BbsPost.builder().id(id).bbsId(bbsId).authorId(1L)
                .title("제목").contentHtml("<p>내용</p>").contentText("내용")
                .status("PUBLISHED").viewCount(0).commentCount(0).build();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-Q-1: 게시글 목록 페이징
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 목록 페이징 — 결과 반환")
    void listPosts_paged_returnsPage() {
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));
        when(bbsPostMapper.findByBbsMasterIdPaged(eq(1L), eq(0), eq(20), eq("ko")))
                .thenReturn(List.of(stubPost(1L, 1L)));
        when(bbsPostMapper.countByBbsMasterId(1L)).thenReturn(1L);

        PageResponse<?> result = postService.listPosts(1L, 0, 20, "ko");

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-Q-2: 게시글 전문검색
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 전문검색 — 키워드로 페이징 결과 반환")
    void searchPosts_byKeyword_returnsPage() {
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));
        when(bbsPostMapper.searchByKeywordPaged(eq(1L), eq("공지"), eq(0), eq(20)))
                .thenReturn(List.of(stubPost(1L, 1L)));
        when(bbsPostMapper.countSearchByKeyword(eq(1L), eq("공지"))).thenReturn(1L);

        PageResponse<?> result = postService.searchPosts(1L, "공지", 0, 20);

        assertThat(result.content()).hasSize(1);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-Q-3: 게시글 단건 상세 + 조회수 dedupe
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 단건 조회 — 로그인 사용자 첫 조회 시 viewCount 증가")
    void getPost_firstViewByLoggedInUser_incrementsViewCount() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));
        when(bbsViewLogMapper.existsRecentView(eq(1L), eq(10L), isNull())).thenReturn(false);
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));

        PostDetail result = postService.getPost(1L, 10L, null);

        assertThat(result).isNotNull();
        verify(bbsPostMapper).incrementViewCount(1L);
        verify(bbsViewLogMapper).insert(any());
    }

    @Test
    @DisplayName("게시글 단건 조회 — 1시간 내 중복 조회 시 viewCount 유지")
    void getPost_duplicateViewWithin1Hour_noViewCountIncrement() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));
        when(bbsViewLogMapper.existsRecentView(eq(1L), eq(10L), isNull())).thenReturn(true);
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));

        postService.getPost(1L, 10L, null);

        verify(bbsPostMapper, never()).incrementViewCount(anyLong());
        verify(bbsViewLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("게시글 단건 조회 — 비로그인 사용자 IP 해시 기반 dedupe")
    void getPost_anonymousUser_ipHashDedupe() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));
        when(bbsViewLogMapper.existsRecentView(eq(1L), isNull(), eq("abc123hash"))).thenReturn(false);
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));

        PostDetail result = postService.getPost(1L, null, "abc123hash");

        assertThat(result).isNotNull();
        verify(bbsPostMapper).incrementViewCount(1L);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-C: 게시글 작성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 작성 — 존재하는 게시판 성공")
    void createPost_validBoard_success() {
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));
        PostCreateRequest request = new PostCreateRequest(
                1L, "제목", "<p>내용</p>", "내용",
                false, null, null, false, null, null, null
        );

        PostDetail result = postService.createPost(request, 1L);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("제목");
        verify(bbsPostMapper).insert(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-U: 게시글 수정 + 이력 보존
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 수정 — 수정 이력 저장 포함")
    void updatePost_savesHistoryBeforeUpdate() {
        BbsPost existing = stubPost(1L, 1L);
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(bbsPostHistoryMapper.nextVersionByPostId(1L)).thenReturn(1);
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L)));

        PostUpdateRequest request = new PostUpdateRequest(
                "수정 제목", "<p>수정 내용</p>", "수정 내용",
                false, null, null, false, "오타 수정"
        );

        PostDetail result = postService.updatePost(1L, request, 1L);

        assertThat(result.title()).isEqualTo("수정 제목");
        verify(bbsPostHistoryMapper).insert(any());
        verify(bbsPostMapper).update(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-D: 게시글 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 — 소프트 삭제 성공")
    void deletePost_existingPost_softDelete() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));

        postService.deletePost(1L, 1L);

        verify(bbsPostMapper).deleteById(1L);
    }

    @Test
    @DisplayName("게시글 존재하지 않는 ID 조회 — PostNotFoundException")
    void getPost_nonExistentId_throwsNotFoundException() {
        when(bbsPostMapper.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(9999L, 1L, null))
                .isInstanceOf(PostNotFoundException.class);
    }
}
