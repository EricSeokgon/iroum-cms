package kr.co.ircp.cms.domain.board.exception;

/**
 * 댓글 기능이 비활성화된 게시판에서 댓글 작성 시 발생하는 예외.
 * REQ-BOARD-003-C: useComment=false 게시판에 댓글 작성 시
 */
public class BoardCommentDisabledException extends RuntimeException {

    public BoardCommentDisabledException(String boardCode) {
        super("댓글 기능이 비활성화된 게시판입니다. code=" + boardCode);
    }
}
