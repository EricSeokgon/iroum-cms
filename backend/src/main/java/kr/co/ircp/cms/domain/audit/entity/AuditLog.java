package kr.co.ircp.cms.domain.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;
import org.apache.ibatis.type.Alias;

import java.time.Instant;

/**
 * 감사 로그 엔티티.
 *
 * <p>SPEC-CMS-005 v0.2.1 §4.2 — audit_log 테이블 매핑.
 * APPEND-ONLY 정책: DB 트리거가 UPDATE/DELETE를 차단한다.
 */
@Alias("AuditLogEntity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    private Long id;

    /** 이벤트 발생 시각 (UTC). */
    private Instant eventTime;

    /** 행위자 사용자 ID (미인증 이벤트는 null). */
    private Long actorId;

    /** 행위자 역할 코드. */
    private String actorRole;

    /**
     * 이벤트 액션 코드.
     * DB CHECK 제약: CREATE/READ/UPDATE/DELETE/LOGIN/LOGIN_FAILURE/LOGOUT 등.
     */
    private String action;

    /** 대상 엔티티 타입 (예: "User", "Menu"). */
    private String entityType;

    /** 대상 엔티티 ID. */
    private String entityId;

    /** 변경 전 값 (JSONB → String 매핑). */
    private String beforeValue;

    /** 변경 후 값 (JSONB → String 매핑). */
    private String afterValue;

    /** 클라이언트 IP 주소 (MDC에서 읽음). */
    private String ipAddress;

    /** 클라이언트 User-Agent (MDC에서 읽음). */
    private String userAgent;

    /** 요청 Trace ID (MDC에서 읽음). */
    private String traceId;

    /** 심각도: INFO / WARN / CRITICAL. */
    private String severity;

    /** 처리 결과: SUCCESS / FAILURE. */
    private String result;

    /** 실패 사유 (result=FAILURE 시). */
    private String failureReason;

    /** 처리 소요 시간 (ms). */
    private Integer durationMs;
}
