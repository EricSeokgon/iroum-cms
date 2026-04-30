package kr.co.ircp.cms.domain.content.popup.exception;

/**
 * 팝업 노출 기간이 유효하지 않을 때 발생하는 예외.
 * REQ-CONTENT-008-D-1: show_from < show_until 검증
 */
public class PopupPeriodInvalidException extends IllegalArgumentException {

    public PopupPeriodInvalidException() {
        super("팝업 노출 기간이 유효하지 않습니다. show_from은 show_until보다 이전이어야 합니다.");
    }
}
