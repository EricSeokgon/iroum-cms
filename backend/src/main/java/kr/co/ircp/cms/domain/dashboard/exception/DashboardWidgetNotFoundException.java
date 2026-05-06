package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-001: 위젯 미존재 → 404 */
public class DashboardWidgetNotFoundException extends RuntimeException {
    public DashboardWidgetNotFoundException(Long id) {
        super("대시보드 위젯을 찾을 수 없습니다. id=" + id);
    }
}
