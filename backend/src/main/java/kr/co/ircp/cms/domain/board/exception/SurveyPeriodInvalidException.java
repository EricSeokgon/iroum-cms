package kr.co.ircp.cms.domain.board.exception;

/**
 * 설문 응답 제출 가능 조건을 위반했을 때 발생하는 예외.
 * REQ-BOARD-013-D-3: 설문 기간 외 / 응답 한도 초과 / 중복 응답 → HTTP 400.
 */
public class SurveyPeriodInvalidException extends RuntimeException {

    public SurveyPeriodInvalidException() {
        super("설문 응답 가능 기간이 아닙니다.");
    }

    public SurveyPeriodInvalidException(String message) {
        super(message);
    }
}
