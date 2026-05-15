package kr.co.ircp.cms.domain.content.page.exception;

/**
 * 요청한 버전의 페이지 이력이 없을 때 발생하는 예외.
 * REQ-CONTENT-005-D-6
 */
public class PageHistoryVersionNotFoundException extends RuntimeException {

    public PageHistoryVersionNotFoundException(int version) {
        super("해당 버전의 이력이 없습니다. version=" + version);
    }
}
