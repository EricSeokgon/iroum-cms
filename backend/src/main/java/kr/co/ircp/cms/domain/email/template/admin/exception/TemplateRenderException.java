package kr.co.ircp.cms.domain.email.template.admin.exception;

/**
 * 템플릿 렌더링 중 예외(Thymeleaf 파싱/치환 오류) 발생 시 변환되는 예외.
 *
 * <p>SPEC-CMS-EMAIL-TEMPLATE-001 REQ-ET-010 — 렌더링 실패를 일관된 타입으로 노출한다.
 * 기존 발송 서비스 연동(EmailTemplateResolver)에서는 이 예외를 잡아 하드코딩 fallback 한다.
 */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
