package kr.co.ircp.cms.domain.email.template.admin.dto;

import kr.co.ircp.cms.domain.email.template.admin.entity.EmailTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 이메일 템플릿 응답 DTO.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 — 엔티티를 그대로 노출하지 않고 응답 형태로 변환.
 */
public record EmailTemplateResponse(
        Long id,
        String code,
        String name,
        String templateType,
        String language,
        String subject,
        String bodyHtml,
        String bodyText,
        List<Map<String, Object>> variables,
        boolean isActive,
        Long createdBy,
        Long updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static EmailTemplateResponse from(EmailTemplate t) {
        return new EmailTemplateResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getTemplateType(),
                t.getLanguage(),
                t.getSubject(),
                t.getBodyHtml(),
                t.getBodyText(),
                t.getVariables(),
                Boolean.TRUE.equals(t.getIsActive()),
                t.getCreatedBy(),
                t.getUpdatedBy(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
