package kr.co.ircp.cms.domain.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보 접근 추적 AOP 어노테이션.
 *
 * <p>REQ-AUTH-018-D-1 — 이 어노테이션이 붙은 메서드가 정상 반환될 때
 * {@code PersonalDataAccessAspect}가 {@code personal_data_access_log}에 비동기로 적재한다.
 *
 * <p>사용 예:
 * <pre>
 * &#64;PersonalDataAccess(
 *     fields = {"email", "name", "phone"},
 *     purpose = "BUSINESS_INQUIRY",
 *     targetUserIdParam = "id"
 * )
 * public UserDetail findById(long id) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersonalDataAccess {

    /**
     * 열람 대상 개인정보 필드 목록.
     *
     * <p>예: {"email", "phone", "name"}
     */
    String[] fields();

    /**
     * 접근 목적 코드.
     *
     * <p>{@code PersonalDataAccessPurpose} 열거형 이름과 일치해야 한다.
     */
    String purpose() default "BUSINESS_INQUIRY";

    /**
     * target_user_id를 담고 있는 메서드 파라미터 이름.
     *
     * <p>AOP Aspect가 이 이름으로 메서드 인자에서 피열람자 ID를 추출한다.
     */
    String targetUserIdParam() default "id";

    /**
     * true이면 열람자(actor)와 피열람자(target)가 동일한 경우에만 로그를 적재한다.
     *
     * <p>본인 정보 조회(/me) 엔드포인트에 사용하며, 타인 정보 조회 시 적재를 건너뛴다.
     */
    boolean selfAccessOnly() default false;
}
