package kr.co.ircp.cms.domain.content.menu.exception;

/**
 * 동일 사이트 내에서 메뉴 코드가 중복될 때 발생하는 예외.
 * REQ-CONTENT-001-D-1: code 유일성 검증
 */
public class MenuCodeDuplicateException extends RuntimeException {

    public MenuCodeDuplicateException(Long siteId, String code) {
        super("동일 사이트 내 메뉴 코드가 이미 존재합니다. siteId=" + siteId + ", code=" + code);
    }
}
