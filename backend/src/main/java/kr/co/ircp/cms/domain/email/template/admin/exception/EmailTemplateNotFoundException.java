package kr.co.ircp.cms.domain.email.template.admin.exception;

/**
 * 이메일 템플릿을 찾을 수 없을 때 발생 (HTTP 404).
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-004/005 — 존재하지 않는 템플릿 조회/수정/삭제.
 */
public class EmailTemplateNotFoundException extends RuntimeException {

    public EmailTemplateNotFoundException(String message) {
        super(message);
    }
}
