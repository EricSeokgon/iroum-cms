package kr.co.ircp.cms.domain.system.accesslog.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 접속 로그 엔티티.
 *
 * <p>REQ-SYSTEM-001-D — 요청 단위 raw 로그 (월별 PARTITION 테이블).
 * IP는 SHA-256(IP + SALT)로 익명화하여 저장한다.
 */
@Getter
@Builder
public class AccessLog {

    private Long id;
    private Long siteId;
    private Long userId;
    private String sessionId;
    /** SHA-256(IP + ACCESS_LOG_IP_SALT) hex 문자열 64자 */
    private String ipHash;
    private String userAgent;
    private String referrer;
    private String pageUrl;
    private Integer statusCode;
    private Integer responseTimeMs;
    private Instant createdAt;
}
