package kr.co.ircp.cms.domain.notification.template.admin.exception;

/**
 * 동일 (code, language) 알림 템플릿 중복 시 발생 (HTTP 409).
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
public class DuplicateNotificationTemplateException extends RuntimeException {

    public DuplicateNotificationTemplateException(String message) {
        super(message);
    }
}
