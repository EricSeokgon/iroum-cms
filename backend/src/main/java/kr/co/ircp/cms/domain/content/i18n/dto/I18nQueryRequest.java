package kr.co.ircp.cms.domain.content.i18n.dto;

/**
 * 다국어 리소스 조회 요청 DTO.
 * REQ-CONTENT-010-D-2: namespace + resourceId + lang 기반 조회
 */
public record I18nQueryRequest(
        String namespace,
        Long resourceId,
        String language
) {}
