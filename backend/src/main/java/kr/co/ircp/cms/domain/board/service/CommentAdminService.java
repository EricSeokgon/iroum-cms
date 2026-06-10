package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.CommentAdminListRequest;
import kr.co.ircp.cms.domain.board.dto.CommentAdminSummary;

/**
 * 관리자 댓글 모더레이션 서비스 인터페이스.
 * SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-001~004 — 전체 댓글 조회·상태 변경·강제 삭제.
 */
public interface CommentAdminService {

    /**
     * 관리자 전체 댓글 목록 페이징 조회 (createdAt DESC).
     * REQ-CMTM-001/002 — 게시판/상태/키워드 필터 지원.
     */
    PageResponse<CommentAdminSummary> listComments(CommentAdminListRequest request);

    /**
     * 댓글 상태 변경 (VISIBLE/HIDDEN).
     * REQ-CMTM-003 — DELETED 댓글은 복구 불가(예외).
     *
     * @return 변경된 댓글 요약
     */
    CommentAdminSummary changeStatus(Long commentId, String status);

    /**
     * 댓글 강제 삭제 (소프트 삭제: status=DELETED, deleted_at=NOW()).
     * REQ-CMTM-004 — 이미 DELETED 인 경우 idempotent.
     */
    void deleteComment(Long commentId);
}
