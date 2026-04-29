package kr.co.ircp.cms.domain.board.exception;

/**
 * 댓글을 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-003-Q: 존재하지 않는 댓글 ID 조회 시
 */
public class CommentNotFoundException extends RuntimeException {

    public CommentNotFoundException(Long id) {
        super("댓글을 찾을 수 없습니다. id=" + id);
    }
}
