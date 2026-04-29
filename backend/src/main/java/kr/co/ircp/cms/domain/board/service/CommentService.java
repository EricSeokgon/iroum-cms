package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;

import java.util.List;

/**
 * 댓글 서비스 인터페이스.
 * REQ-BOARD-003: 댓글 CRUD
 *
 * // @MX:ANCHOR: [AUTO] CommentService — 댓글 비즈니스 계약
 * // @MX:REASON: CommentController, PostDetail 조립 서비스에서 참조 (fan_in >= 3)
 * // @MX:SPEC: REQ-BOARD-003
 */
public interface CommentService {

    /** 게시글 댓글 목록 조회 */
    List<CommentSummary> listComments(Long postId);

    /** 댓글 작성 */
    CommentSummary createComment(Long postId, CommentCreateRequest request, Long authorId);

    /** 댓글 수정 */
    CommentSummary updateComment(Long commentId, String content, Long requesterId);

    /** 댓글 삭제 (소프트 삭제) */
    void deleteComment(Long commentId, Long requesterId);
}
