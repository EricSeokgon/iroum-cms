package kr.co.ircp.cms.domain.board.exception;

/**
 * 댓글 모더레이션 상태 전이가 허용되지 않을 때 발생하는 예외.
 *
 * <p>SPEC-CMS-COMMENT-MODERATE-001 REQ-CMTM-003 — 이미 DELETED 상태인 댓글은
 * VISIBLE/HIDDEN 으로 복구할 수 없다(HTTP 400 으로 매핑).
 */
public class CommentModerationException extends RuntimeException {

    public CommentModerationException(String message) {
        super(message);
    }
}
