package kr.co.ircp.cms.domain.email.template.admin.exception;

/**
 * 비활성({@code is_active=false}) 템플릿으로 실발송/테스트 발송을 시도할 때 발생 (HTTP 409).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-012 — 비활성 템플릿 실발송 거부.
 */
public class TemplateInactiveException extends RuntimeException {

    public TemplateInactiveException(String message) {
        super(message);
    }
}
