package kr.co.ircp.cms.domain.system.code.exception;

/**
 * 코드 그룹에 사용 중인 코드가 있을 때 삭제 시 발생.
 * REQ-SYSTEM-004-D: RESTRICT 제약
 */
public class CodeGroupInUseException extends RuntimeException {

    public CodeGroupInUseException(String groupCode) {
        super("공통코드 그룹 '" + groupCode + "'에 사용 중인 코드가 있어 삭제할 수 없습니다.");
    }
}
