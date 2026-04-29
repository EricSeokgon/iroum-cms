package kr.co.ircp.cms.domain.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드에 감사 로그를 자동으로 적재하는 AOP 어노테이션.
 *
 * <p>SPEC-CMS-005 §7 — {@code AuditLogAspect}가 이 어노테이션이 붙은 메서드를
 * Around로 감싸 성공/실패 여부, 소요 시간, 행위자 정보를 기록한다.
 *
 * <pre>
 * &#64;AuditLog(action = "LOGIN", entityType = "User")
 * public LoginOutcome login(LoginRequest req, ...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * 감사 이벤트 액션 코드.
     * DB CHECK 제약과 일치해야 함:
     * CREATE/READ/UPDATE/DELETE/LOGIN/LOGIN_FAILURE/LOGOUT/
     * PERMISSION_CHANGE/PERMISSION_DENIED/PASSWORD_CHANGE/PASSWORD_RESET/
     * TOKEN_REFRESH/TOKEN_REVOKE/EXPORT/BATCH
     */
    String action();

    /** 대상 엔티티 타입 (예: "User", "Menu"). */
    String entityType() default "";

    /** 심각도: INFO(기본값) / WARN / CRITICAL. */
    String severity() default "INFO";

    /** 메서드 인자를 beforeValue JSON으로 캡처할지 여부. */
    boolean captureArgs() default false;

    /** 반환값을 afterValue JSON으로 캡처할지 여부. */
    boolean captureReturn() default false;
}
