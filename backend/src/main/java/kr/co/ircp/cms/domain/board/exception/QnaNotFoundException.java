package kr.co.ircp.cms.domain.board.exception;

/**
 * Q&A를 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-008: 질문/답변 워크플로
 */
public class QnaNotFoundException extends RuntimeException {

    public QnaNotFoundException(Long id) {
        super("Q&A를 찾을 수 없습니다: " + id);
    }
}
