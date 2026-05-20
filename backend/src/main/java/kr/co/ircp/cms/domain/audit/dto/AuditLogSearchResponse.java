package kr.co.ircp.cms.domain.audit.dto;

import java.util.List;

/**
 * 감사 로그 페이징 검색 응답 DTO.
 */
public record AuditLogSearchResponse(
        List<AuditLogResponse> items,
        long total,
        int page,
        int size
) {}
