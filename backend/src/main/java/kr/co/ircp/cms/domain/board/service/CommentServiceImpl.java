package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.entity.BbsComment;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.BoardCommentDisabledException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsCommentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import kr.co.ircp.cms.domain.board.util.AuthorizationGuard;
import kr.co.ircp.cms.domain.point.service.UserPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 댓글 서비스 구현체.
 * REQ-BOARD-003: 댓글 CRUD
 *
 * // @MX:NOTE: [AUTO] GREEN 단계 구현 완료. 1단계 대댓글만 허용 (DB 트리거가 2단계 이상 차단).
 * // @MX:SPEC: REQ-BOARD-003
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentServiceImpl implements CommentService {

    private final BbsMasterMapper bbsMasterMapper;
    private final BbsPostMapper bbsPostMapper;
    private final BbsCommentMapper bbsCommentMapper;
    private final AuthorizationGuard authorizationGuard;
    // SPEC-CMS-POINTS-001 REQ-PNT-003 — 댓글 작성 best-effort 포인트 적립
    private final UserPointService userPointService;

    @Override
    public List<CommentSummary> listComments(Long postId) {
        List<BbsComment> flat = bbsCommentMapper.findByPostId(postId);
        return buildTree(flat);
    }

    @Override
    @Transactional
    public CommentSummary createComment(Long postId, CommentCreateRequest request, Long authorId) {
        BbsPost post = bbsPostMapper.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        BbsMaster master = bbsMasterMapper.findById(post.getBbsId())
                .orElseThrow(() -> new BbsMasterNotFoundException(post.getBbsId()));
        if (!master.isUseComment()) {
            throw new BoardCommentDisabledException(master.getCode());
        }

        BbsComment comment = BbsComment.builder()
                .postId(postId)
                .parentCommentId(request.parentCommentId())
                .authorId(authorId)
                .anonymousName(request.anonymousName())
                .content(request.content())
                .ipAddress(request.ipAddress())
                .status("VISIBLE")
                .build();
        bbsCommentMapper.insert(comment);

        // SPEC-CMS-POINTS-001 REQ-PNT-003/008: 포인트 적립은 best-effort — 실패해도 댓글 작성은 정상 완료.
        try {
            userPointService.awardForComment(authorId, comment.getId());
        } catch (Exception e) {
            log.warn("포인트 적립 실패 (댓글 ID: {}): {}", comment.getId(), e.getMessage());
        }

        BbsComment saved = bbsCommentMapper.findById(comment.getId())
                .orElse(comment);
        return toSummary(saved, Collections.emptyList());
    }

    @Override
    @Transactional
    public CommentSummary updateComment(Long commentId, String content, Long requesterId) {
        BbsComment comment = bbsCommentMapper.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        // SPEC-CMS-SECURITY-IDOR — 소유권 검증: 작성자 본인 또는 관리자만 수정 허용
        authorizationGuard.ensureOwnerOrAdmin(comment.getAuthorId(), requesterId, "댓글 수정 권한이 없습니다.");
        comment.setContent(content);
        bbsCommentMapper.update(comment);
        return toSummary(comment, Collections.emptyList());
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long requesterId) {
        BbsComment comment = bbsCommentMapper.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        // SPEC-CMS-SECURITY-IDOR — 소유권 검증: 작성자 본인 또는 관리자만 삭제 허용
        authorizationGuard.ensureOwnerOrAdmin(comment.getAuthorId(), requesterId, "댓글 삭제 권한이 없습니다.");
        bbsCommentMapper.deleteById(commentId);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /** 평면 목록을 1단계 트리로 조립. */
    private List<CommentSummary> buildTree(List<BbsComment> flat) {
        Map<Long, List<BbsComment>> replyMap = flat.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(BbsComment::getParentCommentId));

        List<CommentSummary> roots = new ArrayList<>();
        for (BbsComment c : flat) {
            if (c.getParentCommentId() == null) {
                List<BbsComment> childComments = replyMap.getOrDefault(c.getId(), Collections.emptyList());
                List<CommentSummary> childSummaries = childComments.stream()
                        .map(r -> toSummary(r, Collections.emptyList()))
                        .collect(Collectors.toList());
                roots.add(toSummary(c, childSummaries));
            }
        }
        return roots;
    }

    private CommentSummary toSummary(BbsComment c, List<CommentSummary> children) {
        return new CommentSummary(
                c.getId(), c.getPostId(), c.getParentCommentId(),
                c.getAuthorId(), c.getAuthorUsername(), c.getAnonymousName(),
                c.getContent(), c.getStatus(), children,
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
