package kr.co.ircp.cms.domain.content.menu.exception;

/**
 * 메뉴 깊이가 최대치(5)를 초과할 때 발생하는 예외.
 * REQ-CONTENT-001-D-1: depth 검증
 */
public class MenuDepthExceededException extends RuntimeException {

    public MenuDepthExceededException(int requestedDepth) {
        super("메뉴 깊이가 최대 허용치(5)를 초과합니다. 요청된 깊이=" + requestedDepth);
    }
}
