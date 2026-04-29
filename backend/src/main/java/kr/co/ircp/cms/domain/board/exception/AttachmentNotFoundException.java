package kr.co.ircp.cms.domain.board.exception;

/**
 * 첨부파일을 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-004-Q: 존재하지 않는 첨부파일 ID 조회 시
 */
public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException(Long id) {
        super("첨부파일을 찾을 수 없습니다. id=" + id);
    }
}
