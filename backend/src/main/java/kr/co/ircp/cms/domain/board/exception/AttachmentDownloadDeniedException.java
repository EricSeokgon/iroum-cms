package kr.co.ircp.cms.domain.board.exception;

/**
 * 첨부파일 다운로드 서명 URL이 유효하지 않거나 만료되었을 때 발생하는 예외.
 * REQ-BOARD-005: HMAC-SHA256 서명 검증 실패 또는 TTL 만료 시
 */
public class AttachmentDownloadDeniedException extends RuntimeException {

    public AttachmentDownloadDeniedException(String reason) {
        super("첨부파일 다운로드가 거부되었습니다. 사유=" + reason);
    }
}
