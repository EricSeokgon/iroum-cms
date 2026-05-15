package kr.co.ircp.cms.domain.content.page.exception;

/**
 * 페이지를 찾을 수 없거나 공개 불가 상태일 때 발생하는 예외 → 404.
 * REQ-CONTENT-005-D
 */
public class PageNotFoundException extends RuntimeException {

    public PageNotFoundException(String identifier) {
        super("페이지를 찾을 수 없습니다: " + identifier);
    }
}
