package kr.co.ircp.cms.domain.auth.entity;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 권한 변경 이력 엔티티.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-016-D-1 — permission_change_history 테이블 매핑.
 * APPEND-ONLY 정책: DB 트리거로 UPDATE/DELETE 차단.
 */
@Data
@Builder
public class PermissionChangeHistory {

    /** 이력 ID (BIGSERIAL) */
    private Long id;

    /** 변경 유형 */
    private PermissionChangeType changeType;

    /** 대상 사용자 ID (역할 부여/회수 시 설정) */
    private Long targetUserId;

    /** 대상 역할 코드 (역할 관련 변경 시 설정) */
    private String targetRoleCode;

    /**
     * 대상 리소스 식별자.
     * 역할 부여/회수: 역할 코드, 권한 부여/회수: 권한 코드
     */
    private String targetResource;

    /** 변경 수행자 ID */
    private Long changedBy;

    /** 변경 시각 */
    private Instant changedAt;

    /**
     * 심각도.
     * SUPER_ADMIN 역할 변경 → CRITICAL, 그 외 → INFO
     */
    private String severity;

    /** 변경 사유 */
    private String reason;

    /** 변경 수행자 IP 주소 (MDC 추출) */
    private String actorIp;

    /** 분산 추적 ID (MDC 추출) */
    private String traceId;
}
