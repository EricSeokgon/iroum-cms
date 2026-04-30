package kr.co.ircp.cms.domain.content.i18n.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 단일 다국어 리소스 항목 DTO.
 * REQ-CONTENT-010-D: bulk upsert 단위 항목
 */
public record I18nResourceItem(
        @NotBlank String namespace,
        @NotNull Long resourceId,
        @NotBlank String language,
        @NotBlank String fieldName,
        @NotBlank String value
) {}
