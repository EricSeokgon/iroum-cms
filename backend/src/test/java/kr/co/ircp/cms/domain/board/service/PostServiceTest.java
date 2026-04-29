package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.PostCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PostUpdateRequest;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostHistoryMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.repository.BbsViewLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostService RED 단계 테스트.
 * REQ-BOARD-002: 게시글 CRUD + 페이징·검색 + 조회수 dedupe
 *
 * <p>모든 테스트는 Step 2 GREEN 전까지 UnsupportedOperationException으로 실패해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostService RED 테스트 (REQ-BOARD-002)")
class PostServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsPostHistoryMapper bbsPostHistoryMapper;
    @Mock private BbsViewLogMapper bbsViewLogMapper;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(
                bbsMasterMapper, bbsPostMapper, bbsPostHistoryMapper, bbsViewLogMapper
        );
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-Q-1: 게시글 목록 페이징
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 목록 페이징 — 결과 반환")
    void listPosts_paged_returnsPage() {
        assertThatThrownBy(() -> postService.listPosts(1L, 0, 20))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Step 2 GREEN 대기");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-Q-2: 게시글 전문검색
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 전문검색 — 키워드로 페이징 결과 반환")
    void searchPosts_byKeyword_returnsPage() {
        assertThatThrownBy(() -> postService.searchPosts(1L, "공지", 0, 20))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-Q-3: 게시글 단건 상세 + 조회수 dedupe
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 단건 조회 — 로그인 사용자 첫 조회 시 viewCount 증가")
    void getPost_firstViewByLoggedInUser_incrementsViewCount() {
        // GREEN에서: view_log 미존재 → incrementViewCount 호출 + view_log insert 검증
        assertThatThrownBy(() -> postService.getPost(1L, 10L, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("게시글 단건 조회 — 1시간 내 중복 조회 시 viewCount 유지")
    void getPost_duplicateViewWithin1Hour_noViewCountIncrement() {
        // GREEN에서: view_log 존재 → incrementViewCount 미호출 검증
        assertThatThrownBy(() -> postService.getPost(1L, 10L, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("게시글 단건 조회 — 비로그인 사용자 IP 해시 기반 dedupe")
    void getPost_anonymousUser_ipHashDedupe() {
        assertThatThrownBy(() -> postService.getPost(1L, null, "abc123hash"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-C: 게시글 작성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 작성 — 존재하는 게시판 성공")
    void createPost_validBoard_success() {
        PostCreateRequest request = new PostCreateRequest(
                1L, "제목", "<p>내용</p>", "내용",
                false, null, null, false, null, null, null
        );
        assertThatThrownBy(() -> postService.createPost(request, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-U: 게시글 수정 + 이력 보존
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 수정 — 수정 이력 저장 포함")
    void updatePost_savesHistoryBeforeUpdate() {
        // GREEN에서: bbsPostHistoryMapper.insert 호출 후 bbsPostMapper.update 호출 순서 검증
        PostUpdateRequest request = new PostUpdateRequest(
                "수정 제목", "<p>수정 내용</p>", "수정 내용",
                false, null, null, false, "오타 수정"
        );
        assertThatThrownBy(() -> postService.updatePost(1L, request, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-002-D: 게시글 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("게시글 삭제 — 소프트 삭제 성공")
    void deletePost_existingPost_softDelete() {
        assertThatThrownBy(() -> postService.deletePost(1L, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("게시글 존재하지 않는 ID 조회 — PostNotFoundException")
    void getPost_nonExistentId_throwsNotFoundException() {
        assertThatThrownBy(() -> postService.getPost(9999L, 1L, null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
