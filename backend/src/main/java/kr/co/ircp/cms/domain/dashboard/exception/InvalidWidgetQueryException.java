package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-005-D-2: CUSTOM_QUERY 위젯에서 DDL/DML 토큰 검출 시 등록 거부 → 400 */
public class InvalidWidgetQueryException extends RuntimeException {
    public InvalidWidgetQueryException(String message) {
        super(message);
    }
}
