package kr.co.ircp.cms.domain.board.exception;

/**
 * 설문조사를 찾을 수 없을 때 발생하는 예외.
 * REQ-BOARD-013: 설문 단건 조회/수정/삭제/응답 제출 시 미존재 처리 → HTTP 404.
 */
public class SurveyNotFoundException extends RuntimeException {

    public SurveyNotFoundException(Long id) {
        super("설문조사를 찾을 수 없습니다. id=" + id);
    }
}
