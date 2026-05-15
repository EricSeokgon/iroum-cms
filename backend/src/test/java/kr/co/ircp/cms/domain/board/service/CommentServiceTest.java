package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.entity.BbsComment;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.exception.BoardCommentDisabledException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsCommentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CommentService GREEN 단계 테스트.
 * REQ-BOARD-003: 댓글 CRUD
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService GREEN 테스트 (REQ-BOARD-003)")
class CommentServiceTest {

    @Mock private BbsMasterMapper bbsMasterMapper;
    @Mock private BbsPostMapper bbsPostMapper;
    @Mock private BbsCommentMapper bbsCommentMapper;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(bbsMasterMapper, bbsPostMapper, bbsCommentMapper, new AuthorizationGuard());
    }

    private BbsPost stubPost(long id, long bbsId) {
        return BbsPost.builder().id(id).bbsId(bbsId)
                .title("제목").contentHtml("<p>내용</p>")
                .status("PUBLISHED").build();
    }

    private BbsMaster stubMaster(long id, boolean useComment) {
        return BbsMaster.builder().id(id).code("NOTICE").name("공지")
                .type("NOTICE").status("ACTIVE").useComment(useComment).build();
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-Q: 댓글 목록 조회
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 목록 조회 — 게시글 댓글 반환")
    void listComments_byPostId_returnsComments() {
        BbsComment comment = BbsComment.builder()
                .id(1L).postId(1L).content("댓글입니다").status("VISIBLE").build();
        when(bbsCommentMapper.findByPostId(1L)).thenReturn(List.of(comment));

        List<CommentSummary> result = commentService.listComments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("댓글입니다");
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-C: 댓글 작성
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 작성 — 댓글 기능 활성화된 게시판 성공")
    void createComment_enabledBoard_success() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L, true)));

        CommentCreateRequest request = new CommentCreateRequest(
                null, "댓글 내용입니다.", null, null, "192.168.0.1"
        );

        CommentSummary result = commentService.createComment(1L, request, 1L);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("댓글 내용입니다.");
        verify(bbsCommentMapper).insert(any());
    }

    @Test
    @DisplayName("댓글 작성 — 댓글 기능 비활성 게시판이면 BoardCommentDisabledException")
    void createComment_disabledBoard_throwsBoardCommentDisabledException() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L, false)));

        CommentCreateRequest request = new CommentCreateRequest(
                null, "댓글 내용입니다.", null, null, null
        );

        assertThatThrownBy(() -> commentService.createComment(1L, request, 1L))
                .isInstanceOf(BoardCommentDisabledException.class);
    }

    @Test
    @DisplayName("대댓글 작성 — parentCommentId 지정 성공")
    void createComment_withParentCommentId_success() {
        when(bbsPostMapper.findById(1L)).thenReturn(Optional.of(stubPost(1L, 1L)));
        when(bbsMasterMapper.findById(1L)).thenReturn(Optional.of(stubMaster(1L, true)));

        CommentCreateRequest request = new CommentCreateRequest(
                10L, "대댓글 내용입니다.", null, null, null
        );

        CommentSummary result = commentService.createComment(1L, request, 2L);

        assertThat(result).isNotNull();
        assertThat(result.parentCommentId()).isEqualTo(10L);
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-U: 댓글 수정
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 수정 — 작성자 본인 수정 성공")
    void updateComment_byAuthor_success() {
        BbsComment comment = BbsComment.builder()
                .id(1L).postId(1L).authorId(1L)
                .content("원본 내용").status("VISIBLE").build();
        when(bbsCommentMapper.findById(1L)).thenReturn(Optional.of(comment));

        CommentSummary result = commentService.updateComment(1L, "수정된 내용", 1L);

        assertThat(result.content()).isEqualTo("수정된 내용");
        verify(bbsCommentMapper).update(any());
    }

    // ──────────────────────────────────────────────
    // REQ-BOARD-003-D: 댓글 삭제
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("댓글 삭제 — 소프트 삭제 성공")
    void deleteComment_byAuthor_softDelete() {
        BbsComment comment = BbsComment.builder()
                .id(1L).postId(1L).authorId(1L)
                .content("내용").status("VISIBLE").build();
        when(bbsCommentMapper.findById(1L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(1L, 1L);

        verify(bbsCommentMapper).deleteById(1L);
    }

    @Test
    @DisplayName("댓글 삭제 — 존재하지 않으면 CommentNotFoundException")
    void deleteComment_nonExistent_throwsCommentNotFoundException() {
        when(bbsCommentMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.deleteComment(999L, 1L))
                .isInstanceOf(CommentNotFoundException.class);
    }
}
