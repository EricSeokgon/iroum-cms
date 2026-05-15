package kr.co.ircp.cms.domain.content.page.exception;

/**
 * 동일 (siteId, slug) 조합이 이미 존재할 때 발생하는 예외.
 * REQ-CONTENT-005-D-1
 */
public class PageSlugDuplicateException extends RuntimeException {

    public PageSlugDuplicateException(String slug) {
        super("이미 존재하는 slug입니다. slug=" + slug);
    }
}
