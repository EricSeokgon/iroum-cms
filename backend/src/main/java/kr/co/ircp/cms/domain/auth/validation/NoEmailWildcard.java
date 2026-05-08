package kr.co.ircp.cms.domain.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * email 파라미터에 partial 패턴 입력을 거부하는 Bean Validation 어노테이션.
 *
 * <p>REQ-PII-EMAIL-007 — email 컬럼은 HMAC 완전일치 검색만 허용한다.
 * null 및 빈 문자열은 통과(전체 검색 분기로 처리).
 */
@Documented
@Constraint(validatedBy = NoEmailWildcardValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoEmailWildcard {

    String message() default "email 컬럼은 완전일치 검색만 허용됩니다";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
