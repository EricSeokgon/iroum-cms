package kr.co.ircp.cms.domain.content.page.exception;

/**
 * 페이지 slug 패턴이 유효하지 않을 때 발생하는 예외.
 * REQ-CONTENT-005-D-1: ^[a-z0-9][a-z0-9\-/]*$ 검증
 */
public class PageSlugInvalidException extends RuntimeException {

    public PageSlugInvalidException(String slug) {
        super("유효하지 않은 slug 패턴입니다. 소문자/숫자/하이픈/슬래시만 허용됩니다. slug=" + slug);
    }
}
