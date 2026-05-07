package kr.co.ircp.cms.domain.board.exception;

/**
 * 발간자료를 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-012: 발간자료 단건 조회/수정/삭제 시 미존재 처리
 */
public class PublicationNotFoundException extends RuntimeException {

    public PublicationNotFoundException(Long id) {
        super("발간자료를 찾을 수 없습니다. id=" + id);
    }
}
