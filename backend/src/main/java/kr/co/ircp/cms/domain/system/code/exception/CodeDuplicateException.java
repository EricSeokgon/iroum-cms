package kr.co.ircp.cms.domain.system.code.exception;

/**
 * (group_code, code) UNIQUE 위반 시 발생.
 * REQ-SYSTEM-004-D
 */
public class CodeDuplicateException extends RuntimeException {

    public CodeDuplicateException(String groupCode, String code) {
        super("공통코드 중복: groupCode='" + groupCode + "', code='" + code + "'");
    }
}
