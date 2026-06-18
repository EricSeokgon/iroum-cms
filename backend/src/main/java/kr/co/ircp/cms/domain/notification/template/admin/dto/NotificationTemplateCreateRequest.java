package kr.co.ircp.cms.domain.notification.template.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 알림 템플릿 생성 요청.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — code/language 복합 UNIQUE.
 */
public record NotificationTemplateCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @Size(max = 200) String name,
        @Size(max = 20) String channel,
        @Size(max = 300) String subject,
        String bodyHtml,
        String variables,
        @NotBlank @Size(max = 10) String language,
        Boolean isActive,
        Long emailTemplateId) {

    /** 활성 기본값 true. */
    public boolean activeOrDefault() {
        return isActive == null || isActive;
    }
}
