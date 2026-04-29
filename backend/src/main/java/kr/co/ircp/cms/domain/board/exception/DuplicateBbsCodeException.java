package kr.co.ircp.cms.domain.board.exception;

/**
 * 게시판 코드가 중복될 때 발생하는 예외.
 * REQ-BOARD-001-C: code 유일성 제약 위반 시
 */
public class DuplicateBbsCodeException extends RuntimeException {

    public DuplicateBbsCodeException(String code) {
        super("이미 사용 중인 게시판 코드입니다. code=" + code);
    }
}
