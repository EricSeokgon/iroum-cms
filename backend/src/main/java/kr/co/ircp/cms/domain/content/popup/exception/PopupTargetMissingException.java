package kr.co.ircp.cms.domain.content.popup.exception;

/**
 * target_type=ROLE 이지만 역할 코드가 비어있을 때 발생하는 예외.
 * REQ-CONTENT-008-D-1: ROLE 타겟 시 target_role_codes 필수
 */
public class PopupTargetMissingException extends IllegalArgumentException {

    public PopupTargetMissingException() {
        super("target_type=ROLE 일 때 target_role_codes는 비어있을 수 없습니다.");
    }
}
