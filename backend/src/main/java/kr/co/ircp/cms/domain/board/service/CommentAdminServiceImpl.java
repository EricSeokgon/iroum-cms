package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.CommentAdminListRequest;
import kr.co.ircp.cms.domain.board.dto.CommentAdminSummary;
import kr.co.ircp.cms.domain.board.exception.CommentModerationException;
import kr.co.ircp.cms.domain.board.exception.CommentNotFoundException;
import kr.co.ircp.cms.domain.board.repository.BbsCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 관리자 댓글 모더레이션 서비스 구현체.
 * SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-001~004
 *
 * // @MX:ANCHOR: [AUTO] CommentAdminService — 관리자 댓글 모더레이션 비즈니스 계약
 * // @MX:REASON: CommentAdminController 가 목록/상태변경/삭제 3개 경로에서 참조 (fan_in >= 3)
 * // @MX:SPEC: SPEC-CMS-COMMENT-MODERATE-001
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentAdminServiceImpl implements CommentAdminService {

    /** 상태 변경 시 허용되는 목표 상태 (DELETED 로의 직접 변경은 강제삭제 API 전용). */
    private static final Set<String> CHANGEABLE_STATUSES = Set.of("VISIBLE", "HIDDEN");

    private final BbsCommentMapper bbsCommentMapper;

    @Override
    public PageResponse<CommentAdminSummary> listComments(CommentAdminListRequest request) {
        List<CommentAdminSummary> content =
                bbsCommentMapper.listForAdmin(request, request.offset(), request.size());
        long total = bbsCommentMapper.countForAdmin(request);
        return PageResponse.of(content, request.page(), request.size(), total);
    }

    @Override
    @Transactional
    public CommentAdminSummary changeStatus(Long commentId, String status) {
        String target = status == null ? null : status.toUpperCase();
        if (!CHANGEABLE_STATUSES.contains(target)) {
            throw new CommentModerationException(
                    "허용되지 않는 상태 값입니다: " + status + " (VISIBLE/HIDDEN 만 가능)");
        }

        String current = bbsCommentMapper.findStatusById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        // REQ-CMTM-003: 이미 삭제된 댓글은 복구 불가.
        if ("DELETED".equals(current)) {
            throw new CommentModerationException(
                    "이미 삭제된 댓글은 상태를 변경할 수 없습니다. id=" + commentId);
        }

        bbsCommentMapper.updateCommentStatus(commentId, target);
        return bbsCommentMapper.findAdminSummaryById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        // REQ-CMTM-004: 이미 DELETED 면 idempotent — 미존재 시에만 404.
        String current = bbsCommentMapper.findStatusById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if ("DELETED".equals(current)) {
            return;
        }
        bbsCommentMapper.adminSoftDelete(commentId);
    }
}
