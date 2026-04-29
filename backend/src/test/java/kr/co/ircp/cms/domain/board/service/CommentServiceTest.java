package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.repository.BbsCommentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CommentService RED 단계 테스트.
 * REQ-BOARD-003: 댓글 CRUD
 *
 * <p>모든 테스트는 Step 2 GREEN 전까지 UnsupportedOperationException으로 실패해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService RED 테스트 (REQ-BOARD-003)")
class CommentServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsCommentMapper bbsCommentMapper;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(bbsMasterMapper, bbsPostMapper, bbsCommentMapper);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-Q: 댓글 목록 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 목록 조회 — 게시글 댓글 반환")
    void listComments_byPostId_returnsComments() {
        assertThatThrownBy(() -> commentService.listComments(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Step 2 GREEN 대기");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-C: 댓글 작성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 작성 — 댓글 기능 활성화된 게시판 성공")
    void createComment_enabledBoard_success() {
        CommentCreateRequest request = new CommentCreateRequest(
                null, "댓글 내용입니다.", null, null, "192.168.0.1"
        );
        assertThatThrownBy(() -> commentService.createComment(1L, request, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("대댓글 작성 — parentCommentId 지정 성공")
    void createComment_withParentCommentId_success() {
        CommentCreateRequest request = new CommentCreateRequest(
                10L, "대댓글 내용입니다.", null, null, null
        );
        assertThatThrownBy(() -> commentService.createComment(1L, request, 2L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-U: 댓글 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 수정 — 작성자 본인 수정 성공")
    void updateComment_byAuthor_success() {
        assertThatThrownBy(() -> commentService.updateComment(1L, "수정된 내용", 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-D: 댓글 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 삭제 — 소프트 삭제 성공")
    void deleteComment_byAuthor_softDelete() {
        assertThatThrownBy(() -> commentService.deleteComment(1L, 1L))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
