package kr.co.ircp.cms.domain.content.i18n.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 다국어 리소스 bulk upsert 요청 DTO.
 * REQ-CONTENT-010-D: UNIQUE (namespace, resource_id, language, field_name) ON CONFLICT UPDATE
 */
public record I18nUpsertRequest(
        @NotNull List<I18nResourceItem> items
) {}
