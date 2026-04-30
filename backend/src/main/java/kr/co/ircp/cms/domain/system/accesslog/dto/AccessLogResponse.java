package kr.co.ircp.cms.domain.system.accesslog.dto;

import kr.co.ircp.cms.domain.system.accesslog.entity.AccessLog;
import lombok.Builder;

import java.time.Instant;

/**
 * 접속 로그 응답 DTO.
 *
 * <p>REQ-SYSTEM-001-D — GET /api/v1/system/access-logs 응답
 */
@Builder
public record AccessLogResponse(
        Long id,
        Long siteId,
        Long userId,
        String sessionId,
        String ipHash,
        String userAgent,
        String referrer,
        String pageUrl,
        Integer statusCode,
        Integer responseTimeMs,
        Instant createdAt
) {
    public static AccessLogResponse from(AccessLog log) {
        return AccessLogResponse.builder()
                .id(log.getId())
                .siteId(log.getSiteId())
                .userId(log.getUserId())
                .sessionId(log.getSessionId())
                .ipHash(log.getIpHash())
                .userAgent(log.getUserAgent())
                .referrer(log.getReferrer())
                .pageUrl(log.getPageUrl())
                .statusCode(log.getStatusCode())
                .responseTimeMs(log.getResponseTimeMs())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
