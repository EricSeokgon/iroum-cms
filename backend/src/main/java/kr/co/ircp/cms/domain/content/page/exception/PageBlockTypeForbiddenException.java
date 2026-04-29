package kr.co.ircp.cms.domain.content.page.exception;

/**
 * 허용되지 않는 블록 타입을 사용하려 할 때 발생하는 예외.
 * REQ-CONTENT-006-D-1: HTML 블록은 SYSADMIN만 허용
 */
public class PageBlockTypeForbiddenException extends RuntimeException {

    public PageBlockTypeForbiddenException(String blockType) {
        super("해당 블록 타입을 사용할 권한이 없습니다. blockType=" + blockType);
    }
}
