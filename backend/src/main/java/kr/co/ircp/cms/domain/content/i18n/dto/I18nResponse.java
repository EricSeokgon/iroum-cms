package kr.co.ircp.cms.domain.content.i18n.dto;

import java.util.Map;

/**
 * 다국어 리소스 응답 DTO.
 * REQ-CONTENT-010-D-2: fieldName → value 맵으로 반환
 */
public record I18nResponse(
        String namespace,
        Long resourceId,
        String language,
        /** fieldName → value 맵 */
        Map<String, String> fields
) {}
