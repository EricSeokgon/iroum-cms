package kr.co.ircp.cms.domain.board.exception;

/**
 * 첨부파일 크기가 허용 한도를 초과할 때 발생하는 예외.
 * REQ-BOARD-004-C: maxAttachmentSizeKb 초과 업로드 시
 */
public class AttachmentTooLargeException extends RuntimeException {

    public AttachmentTooLargeException(long sizeKb, long maxSizeKb) {
        super(String.format("첨부파일 크기 초과: %dKB > 허용 %dKB", sizeKb, maxSizeKb));
    }
}
