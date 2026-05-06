package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-001-D-3: 사용자 역할이 required_role_codes 에 포함되지 않음 → 403 */
public class WidgetAccessDeniedException extends RuntimeException {
    public WidgetAccessDeniedException(Long widgetId) {
        super("위젯 데이터에 접근할 권한이 없습니다. widgetId=" + widgetId);
    }
}
