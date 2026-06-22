package kr.co.ircp.cms.domain.board.exception;

/**
 * 리뷰를 찾을 수 없을 때 발생하는 예외.
 * SPEC-CMS-REVIEW-001 — 존재하지 않는 리뷰 ID 관리 작업 시.
 */
public class ReviewNotFoundException extends RuntimeException {

    public ReviewNotFoundException(Long id) {
        super("리뷰를 찾을 수 없습니다. id=" + id);
    }
}
