package kr.co.ircp.cms.domain.content.template.exception;

/**
 * 사용 중인 템플릿을 비활성화하려 할 때 발생하는 예외.
 * REQ-CONTENT-004-D-3: 템플릿 비활성화 거부 (page 1건 이상 사용 시)
 */
public class TemplateInUseException extends RuntimeException {

    public TemplateInUseException(Long templateId, long pageCount) {
        super("사용 중인 템플릿은 비활성화할 수 없습니다. templateId=" + templateId + ", 사용 페이지 수=" + pageCount);
    }
}
