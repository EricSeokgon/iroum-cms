package kr.co.ircp.cms.domain.content.template.dto;

import kr.co.ircp.cms.domain.content.template.entity.Template;

import java.time.Instant;

/**
 * 템플릿 응답 DTO.
 * REQ-CONTENT-004-D: 템플릿 조회 응답
 */
public record TemplateResponse(
        Long id,
        String code,
        String name,
        String layoutType,
        String htmlTemplate,
        String cssAssets,
        String jsAssets,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TemplateResponse from(Template template) {
        return new TemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getLayoutType(),
                template.getHtmlTemplate(),
                template.getCssAssets(),
                template.getJsAssets(),
                template.getDescription(),
                template.getStatus(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
