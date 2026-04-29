package kr.co.ircp.cms.domain.content.template.exception;

/**
 * 템플릿에 필수 슬롯({{CONTENT}})이 누락되었을 때 발생하는 예외.
 * REQ-CONTENT-004-D-1: html_template에 {{CONTENT}} 슬롯 존재 검증
 */
public class TemplateMissingSlotException extends RuntimeException {

    public TemplateMissingSlotException(String requiredSlot) {
        super("템플릿에 필수 슬롯이 누락되었습니다. requiredSlot=" + requiredSlot);
    }
}
