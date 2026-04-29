package kr.co.ircp.cms.domain.board.exception;

/**
 * 게시글을 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-002-Q-2: 존재하지 않는 게시글 ID 조회 시
 */
public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(Long id) {
        super("게시글을 찾을 수 없습니다. id=" + id);
    }
}
