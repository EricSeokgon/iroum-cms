package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.board.dto.AdminReviewResponse;

/**
 * 관리자 리뷰 모더레이션 서비스.
 * SPEC-CMS-REVIEW-001 REQ-REV-004/010/011
 */
public interface ReviewAdminService {

    /**
     * 전체 리뷰 목록 (페이징). postId / status 선택 필터.
     * status=null 이면 DELETED 제외 전체, status 지정 시 해당 상태만. REQ-REV-004/011
     */
    PageResponse<AdminReviewResponse> listAll(Long postId, String status, int page, int size);

    /**
     * 리뷰 숨김(HIDDEN). 이미 HIDDEN 이면 멱등. DELETED 는 변경 불가.
     * 처리 후 게시물 집계에서 제외 재계산. REQ-REV-010/011
     */
    void hide(Long reviewId);

    /**
     * 리뷰 삭제(DELETED, 비가역). 이미 DELETED 면 멱등(no-op).
     * 처리 후 게시물 집계에서 제외 재계산. REQ-REV-006/010
     */
    void delete(Long reviewId);
}
