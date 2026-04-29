package kr.co.ircp.cms.domain.content.menu.exception;

/**
 * 메뉴 이동 시 순환 참조가 발생할 때 던져지는 예외.
 * REQ-CONTENT-001-D-4: 자기 자신/자손을 parent로 지정하는 순환 참조 거부
 */
public class MenuCircularReferenceException extends RuntimeException {

    public MenuCircularReferenceException(Long menuId, Long proposedParentId) {
        super("메뉴 순환 참조가 발생합니다. menuId=" + menuId + ", proposedParentId=" + proposedParentId);
    }
}
