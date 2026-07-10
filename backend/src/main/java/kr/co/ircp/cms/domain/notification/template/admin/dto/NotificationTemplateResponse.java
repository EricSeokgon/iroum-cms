package kr.co.ircp.cms.domain.notification.template.admin.dto;

import kr.co.ircp.cms.domain.notification.template.admin.entity.NotificationTemplate;

import java.time.OffsetDateTime;

/**
 * 알림 템플릿 응답.
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
public record NotificationTemplateResponse(
        Long id,
        String code,
        String name,
        String channel,
        String subject,
        String bodyHtml,
        String variables,
        String language,
        Boolean isActive,
        Long emailTemplateId,
        Long createdBy,
        Long updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /** 엔티티 → 응답 변환. */
    public static NotificationTemplateResponse from(NotificationTemplate t) {
        return new NotificationTemplateResponse(
                t.getId(), t.getCode(), t.getName(), t.getChannel(),
                t.getSubject(), t.getBodyHtml(), t.getVariables(), t.getLanguage(),
                t.getIsActive(), t.getEmailTemplateId(),
                t.getCreatedBy(), t.getUpdatedBy(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
