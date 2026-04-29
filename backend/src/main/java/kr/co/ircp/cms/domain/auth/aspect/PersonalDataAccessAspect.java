package kr.co.ircp.cms.domain.auth.aspect;

import kr.co.ircp.cms.domain.auth.annotation.PersonalDataAccess;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessPurpose;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.auth.service.PersonalDataAccessLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.util.Set;

/**
 * {@code @PersonalDataAccess} 어노테이션 처리 AOP Aspect.
 *
 * <p>REQ-AUTH-018-D-1 — @AfterReturning으로 정상 반환 시에만 로그를 적재하며,
 * 예외 발생(인가 거부·조회 실패) 시에는 로그를 남기지 않는다.
 *
 * <p>Order를 지정하지 않아 {@code AuditLogAspect}(Order=1)보다 우선순위가 낮게 실행되어
 * 비즈니스 로직과 감사 로그 이후에 개인정보 접근 로그가 적재된다.
 */
// @MX:ANCHOR: [AUTO] PersonalDataAccessAspect.afterAccess — @PersonalDataAccess 처리 핵심 진입점
// @MX:REASON: UserServiceImpl(findById/update/getMe), 테스트 Mock, PersonalDataAccessLogService 참조 (fan_in >= 3)
// @MX:WARN: [AUTO] extractTargetUserId — 리플렉션으로 파라미터명 추출; -parameters 컴파일 옵션 필요
// @MX:REASON: JVM 기본값에서 파라미터명은 제거된다. build.gradle에 compileJava.options.parameters=true 설정 필요.
@Aspect
@Component
@RequiredArgsConstructor
public class PersonalDataAccessAspect {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataAccessAspect.class);

    private final PersonalDataAccessLogService logService;

    /**
     * @PersonalDataAccess 메서드가 정상 반환될 때 개인정보 접근 로그를 비동기 적재한다.
     *
     * <p>예외 발생 시 적재 생략 — 개인정보에 실제 접근하지 않은 경우이기 때문.
     */
    @AfterReturning(value = "@annotation(annotation)", returning = "result")
    public void afterAccess(JoinPoint pjp, PersonalDataAccess annotation, Object result) {
        try {
            // SecurityContext에서 열람자(actor) 추출
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof JwtPrincipal viewer)) {
                return;
            }

            // target_user_id 추출 (메서드 파라미터에서)
            Long targetUserId = extractTargetUserId(pjp, annotation.targetUserIdParam());
            if (targetUserId == null) {
                return;
            }

            // selfAccessOnly=true이고 열람자 != 피열람자이면 적재 생략
            if (annotation.selfAccessOnly() && viewer.userId() != targetUserId) {
                return;
            }

            String viewerRole = viewer.roles().stream().findFirst().orElse(null);
            Set<String> fields = Set.of(annotation.fields());
            PersonalDataAccessPurpose purpose = PersonalDataAccessPurpose.valueOf(annotation.purpose());

            logService.record(viewer.userId(), viewerRole, targetUserId, fields, purpose);
        } catch (Exception e) {
            log.error("개인정보 접근 로그 캡처 실패 (non-blocking, REQ-AUTH-018)", e);
        }
    }

    /**
     * 메서드 파라미터 중 {@code paramName}과 일치하는 파라미터 값을 Long으로 추출한다.
     *
     * <p>컴파일 옵션 {@code -parameters}가 설정된 경우 파라미터명이 유지된다.
     * 없는 경우 {@code arg0, arg1...} 형태가 되므로 build.gradle에 설정 필요.
     */
    private Long extractTargetUserId(JoinPoint pjp, String paramName) {
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Parameter[] params = sig.getMethod().getParameters();
            Object[] args = pjp.getArgs();
            for (int i = 0; i < params.length; i++) {
                if (paramName.equals(params[i].getName()) && args[i] != null) {
                    if (args[i] instanceof Long l) return l;
                    if (args[i] instanceof Integer n) return n.longValue();
                    if (args[i] instanceof Number n) return n.longValue();
                }
            }
        } catch (Exception e) {
            log.debug("targetUserId 파라미터 추출 실패 (무시) — paramName={}", paramName, e);
        }
        return null;
    }
}
