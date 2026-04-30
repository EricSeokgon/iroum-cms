package kr.co.ircp.cms.domain.system.accesslog.dto;

import java.time.LocalDate;

/**
 * 접속 로그 검색 요청 DTO.
 *
 * <p>REQ-SYSTEM-001-D — GET /api/v1/system/access-logs 쿼리 파라미터
 */
public record AccessLogSearchRequest(
        LocalDate from,
        LocalDate to,
        Integer statusCode,
        String pageUrl,
        int page,
        int size
) {
    public AccessLogSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
    }

    public int offset() {
        return page * size;
    }
}
