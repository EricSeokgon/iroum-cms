package kr.co.ircp.cms.domain.notification.template.admin.exception;

/**
 * 알림 템플릿을 찾을 수 없을 때 발생 (HTTP 404).
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
public class NotificationTemplateNotFoundException extends RuntimeException {

    public NotificationTemplateNotFoundException(String message) {
        super(message);
    }
}
