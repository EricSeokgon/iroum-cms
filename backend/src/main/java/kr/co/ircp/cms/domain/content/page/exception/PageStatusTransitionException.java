package kr.co.ircp.cms.domain.content.page.exception;

/**
 * 페이지 상태 전이가 허용되지 않을 때 발생하는 예외.
 * REQ-CONTENT-005-D: 상태 전이 검증 (예: PUBLISHED → DRAFT 직접 전이 불허)
 */
public class PageStatusTransitionException extends RuntimeException {

    public PageStatusTransitionException(String currentStatus, String targetStatus) {
        super("허용되지 않은 페이지 상태 전이입니다. " + currentStatus + " → " + targetStatus);
    }
}
