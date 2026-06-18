package kr.co.ircp.cms.domain.notification.template.admin.dto;

/**
 * 알림 템플릿 미리보기 결과 (치환 완료된 subject/body).
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
public record NotificationTemplatePreviewResult(
        String subject,
        String bodyHtml) {
}
