package kr.co.ircp.cms.domain.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * {@link NoEmailWildcard} 어노테이션 구현체.
 *
 * <p>REQ-PII-EMAIL-007 — email 컬럼은 HMAC 완전일치 검색만 허용한다.
 * - null → valid (파라미터 미포함)
 * - 빈 문자열 → valid (전체 검색 분기)
 * - RFC 5321 valid email (와일드카드 없음) → valid
 * - 와일드카드(*, %, _, ?)·정규식 메타문자·@ 미포함·@-trailing → invalid
 */
public class NoEmailWildcardValidator implements ConstraintValidator<NoEmailWildcard, String> {

    /**
     * RFC 5321 기반 허용 email 패턴.
     *
     * <p>허용: local-part + @ + domain + . + tld (RFC 5321 허용: +, -, . 포함)
     * 금지 문자(SQL·glob 와일드카드): *, %, _, ?
     * 금지 문자(구조 훼손): [, ], {, }, (, ), ^, $, \
     * + 는 RFC 5321 로컬파트에서 허용되므로 제외하지 않음 (user+tag@example.com 형태)
     */
    private static final Pattern VALID_EMAIL_PATTERN = Pattern.compile(
            "^[^@*%_?\\[\\]{}()^$\\\\]+"
            + "@"
            + "[^@*%_?\\[\\]{}()^$\\\\]+"
            + "\\."
            + "[^@*%_?\\[\\]{}()^$\\\\]+$"
    );

    @Override
    public void initialize(NoEmailWildcard constraintAnnotation) {
        // 초기화 불필요
    }

    /**
     * email 파라미터 유효성 검증.
     *
     * <p>null과 빈 문자열은 "파라미터 미포함" 또는 "전체 검색"으로 처리하므로 통과.
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return VALID_EMAIL_PATTERN.matcher(value).matches();
    }
}
