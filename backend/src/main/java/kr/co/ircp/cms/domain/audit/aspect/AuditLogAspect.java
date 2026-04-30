package kr.co.ircp.cms.domain.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.audit.service.AuditLogService.AuditLogRecord;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.Instant;

/**
 * @AuditLog 어노테이션 처리 AOP Aspect.
 *
 * <p>SPEC-CMS-005 §7 — @AuditLog가 붙은 메서드를 Around로 감싸
 * 성공/실패 여부, 소요 시간, 행위자 정보를 감사 로그에 기록한다.
 *
 * <p>Order(1) — @Transactional보다 바깥쪽에서 실행되어 트랜잭션 결과를 포함한 로그를 남긴다.
 */
// @MX:ANCHOR: [AUTO] AuditLogAspect.around — @AuditLog 처리 핵심 로직
// @MX:REASON: AuthServiceImpl @AuditLog 메서드, 테스트, AuditLogService 참조 (fan_in >= 3)
// @MX:WARN: [AUTO] ProceedingJoinPoint.proceed() — 예외를 re-throw해야 비즈니스 로직에 전파됨
// @MX:REASON: finally 블록에서 감사 로그를 기록하므로 예외 흡수 금지. thrown 변수로 구분
@Aspect
@Component
@Order(1)
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * @AuditLog 어노테이션 또는 Service C/U/D 메서드 패턴 매칭 시 감사 로그를 비동기 적재한다.
     *
     * <p>REQ-CROSS-001-D-1/2 — @AuditLog가 붙은 메서드와
     * 도메인 Service의 create/update/delete 접두사 메서드 모두 자동 포착.
     *
     * <p>비즈니스 로직 결과(성공/예외)는 그대로 전파하며, 감사 로그 실패는 흡수한다.
     */
    @Around("@annotation(auditLog) || (" +
            "  execution(* kr.co.ircp.cms.domain..*Service.create*(..)) ||" +
            "  execution(* kr.co.ircp.cms.domain..*Service.update*(..)) ||" +
            "  execution(* kr.co.ircp.cms.domain..*Service.delete*(..))" +
            ")")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Instant start = Instant.now();
        Object result = null;
        Throwable thrown = null;

        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            long ms = Duration.between(start, Instant.now()).toMillis();
            captureAuditLog(pjp, auditLog, result, thrown, (int) ms);
        }
    }

    /** 감사 로그 캡처 (비동기 적재). 내부 실패는 ERROR 로그로 흡수. */
    private void captureAuditLog(
            ProceedingJoinPoint pjp,
            AuditLog auditLog,
            Object result,
            Throwable thrown,
            int durationMs) {
        try {
            // 행위자 추출 (SecurityContext)
            Long actorId = null;
            String actorRole = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof JwtPrincipal p) {
                actorId = p.userId();
                actorRole = p.roles().stream().findFirst().orElse(null);
            }

            // entityId 추출 — 파라미터 중 "id"라는 이름 우선
            String entityId = extractEntityId(pjp);

            String resultStatus = thrown == null ? "SUCCESS" : "FAILURE";
            String failureReason = thrown == null
                    ? null
                    : thrown.getClass().getSimpleName() + ": " + thrown.getMessage();

            // @AuditLog가 없는 패턴 매칭 케이스: action을 메서드명에서 추론
            String action;
            String entityType;
            String severity;
            if (auditLog != null) {
                action     = auditLog.action();
                entityType = auditLog.entityType();
                severity   = auditLog.severity();
            } else {
                // 메서드명 prefix에서 action 추론
                String methodName = pjp.getSignature().getName().toLowerCase();
                if (methodName.startsWith("create")) {
                    action = "CREATE";
                } else if (methodName.startsWith("update")) {
                    action = "UPDATE";
                } else if (methodName.startsWith("delete")) {
                    action = "DELETE";
                } else {
                    action = "AUTO";
                }
                entityType = pjp.getSignature().getDeclaringType().getSimpleName();
                severity   = "INFO";
            }

            // 캡처 옵션 — @AuditLog가 있을 때만 beforeJson/afterJson 캡처
            String beforeJson = null;
            String afterJson  = null;
            if (auditLog != null) {
                if (auditLog.captureArgs()) {
                    beforeJson = toJson(pjp.getArgs());
                }
                if (auditLog.captureReturn() && result != null && thrown == null) {
                    afterJson = toJson(result);
                }
            }

            auditLogService.record(new AuditLogRecord(
                    Instant.now(),
                    actorId,
                    actorRole,
                    action,
                    entityType,
                    entityId,
                    beforeJson,
                    afterJson,
                    MDC.get("ipAddress"),
                    MDC.get("userAgent"),
                    MDC.get("traceId"),
                    severity,
                    resultStatus,
                    failureReason,
                    durationMs
            ));
        } catch (Exception e) {
            log.error("감사 로그 캡처 실패 (non-blocking)", e);
        }
    }

    /**
     * 메서드 파라미터 중 "id"라는 이름의 값을 entityId로 추출.
     *
     * <p>v0.2+에서 @AuditLog(idExpression="#user.id") SpEL 지원 예정.
     */
    private String extractEntityId(ProceedingJoinPoint pjp) {
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Parameter[] params = sig.getMethod().getParameters();
            Object[] args = pjp.getArgs();
            for (int i = 0; i < params.length; i++) {
                if ("id".equalsIgnoreCase(params[i].getName()) && args[i] != null) {
                    return String.valueOf(args[i]);
                }
            }
        } catch (Exception e) {
            log.debug("entityId 추출 실패 (무시)", e);
        }
        return null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
