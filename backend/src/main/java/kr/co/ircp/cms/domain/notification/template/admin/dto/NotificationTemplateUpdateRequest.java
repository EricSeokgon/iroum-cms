package kr.co.ircp.cms.domain.notification.template.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * 알림 템플릿 수정 요청 (부분 수정 — 모든 필드 nullable).
 *
 * <p>SPEC-CMS-NOTI-EXT-001.
 */
public record NotificationTemplateUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 20) String channel,
        @Size(max = 300) String subject,
        String bodyHtml,
        String variables,
        Boolean isActive,
        Long emailTemplateId) {
}
