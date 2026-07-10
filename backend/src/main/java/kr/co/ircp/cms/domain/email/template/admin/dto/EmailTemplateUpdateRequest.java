package kr.co.ircp.cms.domain.email.template.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 이메일 템플릿 수정 요청 (REQ-ET-004).
 *
 * <p>code/language는 유니크 식별자라 수정 대상에서 제외한다(생성 시 확정).
 */
public record EmailTemplateUpdateRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank String templateType,
        @NotBlank @Size(max = 500) String subject,
        @NotBlank String bodyHtml,
        String bodyText,
        List<Map<String, Object>> variables,
        Boolean isActive) {

    public boolean activeOrDefault() {
        return isActive == null || isActive;
    }
}
