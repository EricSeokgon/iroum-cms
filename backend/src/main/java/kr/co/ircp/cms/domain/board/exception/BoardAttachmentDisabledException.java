package kr.co.ircp.cms.domain.board.exception;

/**
 * 첨부파일 기능이 비활성화된 게시판에서 업로드 시 발생하는 예외.
 * REQ-BOARD-004-C: useAttachment=false 게시판에 파일 업로드 시
 */
public class BoardAttachmentDisabledException extends RuntimeException {

    public BoardAttachmentDisabledException(String boardCode) {
        super("첨부파일 기능이 비활성화된 게시판입니다. code=" + boardCode);
    }
}
