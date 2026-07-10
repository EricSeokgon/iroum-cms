package kr.co.ircp.cms.domain.email.template.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 이메일 템플릿 생성 요청 (REQ-ET-001).
 */
public record EmailTemplateCreateRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank String templateType,
        String language,
        @NotBlank @Size(max = 500) String subject,
        @NotBlank String bodyHtml,
        String bodyText,
        List<Map<String, Object>> variables,
        Boolean isActive) {

    /** 언어 기본값 ko. */
    public String languageOrDefault() {
        return (language == null || language.isBlank()) ? "ko" : language;
    }

    /** 활성 기본값 true. */
    public boolean activeOrDefault() {
        return isActive == null || isActive;
    }
}
