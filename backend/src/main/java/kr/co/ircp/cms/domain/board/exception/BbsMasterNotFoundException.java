package kr.co.ircp.cms.domain.board.exception;

/**
 * 게시판 마스터를 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-001-Q-2: 존재하지 않는 게시판 코드/ID 조회 시
 */
public class BbsMasterNotFoundException extends RuntimeException {

    public BbsMasterNotFoundException(Long id) {
        super("게시판을 찾을 수 없습니다. id=" + id);
    }

    public BbsMasterNotFoundException(String code) {
        super("게시판을 찾을 수 없습니다. code=" + code);
    }
}
