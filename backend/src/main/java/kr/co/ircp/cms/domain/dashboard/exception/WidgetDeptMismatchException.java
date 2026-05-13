package kr.co.ircp.cms.domain.dashboard.exception;

/**
 * REQ-VIZ-001-D-8 A-8: DEPT_ADMIN 이 타 부서(organization) 소속 사용자가 만든 위젯을
 * 수정하려 할 때 발생하는 예외.
 *
 * <p>HTTP 403 Forbidden + errorCode = WIDGET_DEPT_MISMATCH 로 매핑된다
 * ({@link kr.co.ircp.cms.config.GlobalExceptionHandler} 참조).
 */
public class WidgetDeptMismatchException extends RuntimeException {
    public WidgetDeptMismatchException(Long widgetId, Long requesterId) {
        super("부서 범위 밖의 위젯입니다. widgetId=" + widgetId + ", requesterId=" + requesterId);
    }
}
