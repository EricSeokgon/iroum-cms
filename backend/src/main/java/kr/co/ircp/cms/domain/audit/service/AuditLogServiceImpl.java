package kr.co.ircp.cms.domain.audit.service;

import kr.co.ircp.cms.domain.audit.entity.AuditLog;
import kr.co.ircp.cms.domain.audit.repository.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 감사 로그 서비스 구현체.
 *
 * <p>SPEC-CMS-005 §7 — 비동기 실행으로 비즈니스 로직 영향 최소화.
 * 실패 시 fallback: ERROR 레벨 로깅 (v0.3에서 별도 fallback 큐 도입 예정).
 */
// @MX:WARN: [AUTO] @Async("auditExecutor") — 예외가 호출자에 전파되지 않음
// @MX:REASON: AuditLogAspect finally 블록에서 호출됨. 예외 무시는 의도적이나 로그 유실 위험 존재
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 감사 로그를 비동기(auditExecutor)로 DB에 적재한다.
     *
     * <p>예외는 ERROR 로깅으로 흡수 — 비즈니스 로직에 절대 전파하지 않는다.
     */
    @Override
    @Async("auditExecutor")
    public void record(AuditLogRecord entry) {
        try {
            AuditLog entity = AuditLog.builder()
                    .eventTime(entry.eventTime() != null ? entry.eventTime() : Instant.now())
                    .actorId(entry.actorId())
                    .actorRole(entry.actorRole())
                    .action(entry.action())
                    .entityType(entry.entityType())
                    .entityId(entry.entityId())
                    .beforeValue(entry.beforeValue())
                    .afterValue(entry.afterValue())
                    .ipAddress(entry.ipAddress())
                    .userAgent(entry.userAgent())
                    .traceId(entry.traceId())
                    .severity(entry.severity() != null ? entry.severity() : "INFO")
                    .result(entry.result() != null ? entry.result() : "SUCCESS")
                    .failureReason(entry.failureReason())
                    .durationMs(entry.durationMs())
                    .build();

            auditLogMapper.insert(entity);
        } catch (Exception e) {
            // 감사 로그 실패는 비즈니스 로직에 영향을 주지 않아야 함
            log.error("감사 로그 적재 실패 (non-blocking) action={} entityType={} actorId={}",
                    entry.action(), entry.entityType(), entry.actorId(), e);
        }
    }
}
