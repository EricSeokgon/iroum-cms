package kr.co.ircp.cms.domain.notification.admin.exception;

/**
 * REQ-NC-002 / REQ-NC-004 — 존재하지 않거나 권한 없는 알림 접근 → 404 / 403 매핑.
 *
 * <p>본 SPEC 은 권한 격리(REQ-NC-010)를 위해 본인 소유가 아닌 알림도 404 대신
 * 403 응답을 권장하지만, "존재 자체" 의 enumeration 방지를 위해 단일 예외로 통일하고
 * GlobalExceptionHandler 에서 403 으로 매핑한다.
 */
public class AdminNotificationNotFoundException extends RuntimeException {
    public AdminNotificationNotFoundException(Long id) {
        super("알림을 찾을 수 없거나 접근 권한이 없습니다. id=" + id);
    }
}
