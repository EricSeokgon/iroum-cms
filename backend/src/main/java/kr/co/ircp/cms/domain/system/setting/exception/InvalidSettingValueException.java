package kr.co.ircp.cms.domain.system.setting.exception;

/**
 * 시스템 설정 값 타입 검증 실패 시 발생.
 * REQ-SYSTEM-005-D: value_type 검증
 */
public class InvalidSettingValueException extends RuntimeException {

    public InvalidSettingValueException(String key, String valueType, String value) {
        super("시스템 설정 값 타입 오류: key='" + key + "' valueType='" + valueType + "' value='" + value + "'");
    }
}
