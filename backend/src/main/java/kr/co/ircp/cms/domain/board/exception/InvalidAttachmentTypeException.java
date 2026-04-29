package kr.co.ircp.cms.domain.board.exception;

/**
 * 허용되지 않는 첨부파일 MIME 타입일 때 발생하는 예외.
 * REQ-BOARD-004-C: 허용 MIME 타입 외 업로드 시
 */
public class InvalidAttachmentTypeException extends RuntimeException {

    public InvalidAttachmentTypeException(String mimeType) {
        super("허용되지 않는 파일 형식입니다. mimeType=" + mimeType);
    }
}
