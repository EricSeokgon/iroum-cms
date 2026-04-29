package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.repository.BbsCommentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 댓글 서비스 구현체.
 * REQ-BOARD-003: 댓글 CRUD
 *
 * // @MX:TODO: [AUTO] Step 2 GREEN — 실제 구현 필요. 현재 모든 메서드가 스텁 상태.
 * // @MX:SPEC: REQ-BOARD-003
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsCommentMapper bbsCommentMapper;

    @Override
    public List<CommentSummary> listComments(Long postId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public CommentSummary createComment(Long postId, CommentCreateRequest request, Long authorId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public CommentSummary updateComment(Long commentId, String content, Long requesterId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long requesterId) {
        throw new UnsupportedOperationException("Step 2 GREEN 대기");
    }
}
