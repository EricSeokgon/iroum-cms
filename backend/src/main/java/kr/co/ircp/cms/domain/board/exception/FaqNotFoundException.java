package kr.co.ircp.cms.domain.board.exception;

/**
 * FAQ를 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-007: FAQ 카테고리·정렬·검색
 */
public class FaqNotFoundException extends RuntimeException {

    public FaqNotFoundException(Long id) {
        super("FAQ를 찾을 수 없습니다: " + id);
    }
}
