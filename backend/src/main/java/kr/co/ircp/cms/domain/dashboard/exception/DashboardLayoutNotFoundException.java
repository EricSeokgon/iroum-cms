package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-002: 대시보드 레이아웃 미존재 → 404 */
public class DashboardLayoutNotFoundException extends RuntimeException {
    public DashboardLayoutNotFoundException(Long id) {
        super("대시보드 레이아웃을 찾을 수 없습니다. id=" + id);
    }
}
