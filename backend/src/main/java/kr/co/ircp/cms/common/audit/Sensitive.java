package kr.co.ircp.cms.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 직렬화 시 개인정보 마스킹 대상을 표시하는 어노테이션.
 *
 * <p>REQ-CROSS-001-D-4 — {@code SensitiveFieldMasker}가 이 어노테이션이 붙은
 * 필드 또는 키를 자동으로 "***"로 치환한다.
 *
 * <pre>
 * public class UserDto {
 *     &#64;Sensitive
 *     private String password;
 *
 *     &#64;Sensitive
 *     private String phone;
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {
}
