package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.CommentCreateRequest;
import kr.co.ircp.cms.domain.board.dto.CommentSummary;
import kr.co.ircp.cms.domain.board.entity.BbsComment;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.entity.BbsPost;
import kr.co.ircp.cms.domain.board.exception.BoardCommentDisabledException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.exception.PostNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsCommentMapper;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import kr.co.ircp.cms.domain.board.repository.BbsPostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 댓글 서비스 구현체.
 * REQ-BOARD-003: 댓글 CRUD
 *
 * // @MX:NOTE: [AUTO] GREEN 단계 구현 완료. 1단계 대댓글만 허용 (DB 트리거가 2단계 이상 차단).
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
        List<BbsComment> flat = bbsCommentMapper.findByPostId(postId);
        return buildTree(flat);
    }

    @Override
    @Transactional
    public CommentSummary createComment(Long postId, CommentCreateRequest request, Long authorId) {
        BbsPost post = bbsPostMapper.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        BbsMaster master = bbsMasterMapper.findById(post.getBbsId())
                .orElse(null);
        if (master != null && !master.isUseComment()) {
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

        return toSummary(comment, Collections.emptyList());
    }

    @Override
    @Transactional
    public CommentSummary updateComment(Long commentId, String content, Long requesterId) {
        BbsComment comment = bbsCommentMapper.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        // SPEC-CMS-SECURITY-IDOR — 소유권 검증: 작성자 본인 또는 관리자만 수정 허용
        ensureOwnerOrAdmin(comment.getAuthorId(), requesterId, "댓글 수정 권한이 없습니다.");
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
        ensureOwnerOrAdmin(comment.getAuthorId(), requesterId, "댓글 삭제 권한이 없습니다.");
        bbsCommentMapper.deleteById(commentId);
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    /**
     * 소유권 또는 관리자 권한 검증.
     *
     * <p>SPEC-CMS-SECURITY-IDOR — 본인 또는 ADMIN/SUPER_ADMIN/CONTENT_ADMIN 권한 보유자만 허용.
     */
    private void ensureOwnerOrAdmin(Long ownerId, Long requesterId, String denyMessage) {
        if (requesterId != null && Objects.equals(ownerId, requesterId)) {
            return;
        }
        if (currentUserIsAdmin()) {
            return;
        }
        throw new AccessDeniedException(denyMessage);
    }

    /** SecurityContext에서 ADMIN/SUPER_ADMIN/CONTENT_ADMIN 권한 여부 확인. */
    private boolean currentUserIsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return "ROLE_ADMIN".equals(role)
                    || "ROLE_SUPER_ADMIN".equals(role)
                    || "ROLE_CONTENT_ADMIN".equals(role);
        });
    }

    /** 평면 목록을 1단계 트리로 조립. */
    private List<CommentSummary> buildTree(List<BbsComment> flat) {
        Map<Long, List<BbsComment>> replyMap = flat.stream()
                .filter(c -> c.getParentCommentId() != null)
                .collect(Collectors.groupingBy(BbsComment::getParentCommentId));

        List<CommentSummary> roots = new ArrayList<>();
        for (BbsComment c : flat) {
            if (c.getParentCommentId() == null) {
                List<BbsComment> replies = replyMap.getOrDefault(c.getId(), Collections.emptyList());
                List<CommentSummary> replySummaries = replies.stream()
                        .map(r -> toSummary(r, Collections.emptyList()))
                        .collect(Collectors.toList());
                roots.add(toSummary(c, replySummaries));
            }
        }
        return roots;
    }

    private CommentSummary toSummary(BbsComment c, List<CommentSummary> replies) {
        return new CommentSummary(
                c.getId(), c.getPostId(), c.getParentCommentId(),
                c.getAuthorId(), null, c.getAnonymousName(),
                c.getContent(), c.getStatus(), replies,
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
