package kr.co.ircp.cms.domain.email.template.admin.exception;

/**
 * 동일 {@code code}+{@code language} 조합의 템플릿이 이미 존재할 때 발생 (HTTP 409).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-002 — code+language 유니크 제약 위반.
 */
public class DuplicateEmailTemplateException extends RuntimeException {

    public DuplicateEmailTemplateException(String message) {
        super(message);
    }
}
