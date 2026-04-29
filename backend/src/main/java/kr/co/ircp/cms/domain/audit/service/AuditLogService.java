package kr.co.ircp.cms.domain.audit.service;

import java.time.Instant;

/**
 * 감사 로그 서비스 인터페이스.
 *
 * <p>SPEC-CMS-005 §7 — AuditLogAspect가 호출하는 단일 적재 메서드.
 * 구현체는 비동기 실행으로 비즈니스 로직 응답 지연을 최소화한다.
 */
// @MX:ANCHOR: [AUTO] AuditLogService.record — 감사 로그 적재 계약; 변경 시 Aspect·테스트 영향
// @MX:REASON: AuditLogAspect, AuthServiceImpl @AuditLog, 테스트 Mock 등 fan_in >= 3
public interface AuditLogService {

    /**
     * 감사 로그 항목을 비동기로 적재한다.
     *
     * <p>실패 시 비즈니스 로직에 예외를 전파하지 않고 ERROR 레벨로 로깅한다.
     */
    void record(AuditLogRecord entry);

    /**
     * 감사 로그 데이터 레코드.
     *
     * @param eventTime     이벤트 발생 시각
     * @param actorId       행위자 사용자 ID (미인증 시 null)
     * @param actorRole     행위자 역할 코드
     * @param action        이벤트 액션 코드 (DB CHECK 제약과 일치)
     * @param entityType    대상 엔티티 타입
     * @param entityId      대상 엔티티 ID
     * @param beforeValue   변경 전 값 (JSON 문자열)
     * @param afterValue    변경 후 값 (JSON 문자열)
     * @param ipAddress     클라이언트 IP
     * @param userAgent     클라이언트 User-Agent
     * @param traceId       요청 Trace ID
     * @param severity      심각도 (INFO/WARN/CRITICAL)
     * @param result        처리 결과 (SUCCESS/FAILURE)
     * @param failureReason 실패 사유
     * @param durationMs    처리 소요 시간 (ms)
     */
    record AuditLogRecord(
            Instant eventTime,
            Long actorId,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String beforeValue,
            String afterValue,
            String ipAddress,
            String userAgent,
            String traceId,
            String severity,
            String result,
            String failureReason,
            Integer durationMs
    ) {}
}
