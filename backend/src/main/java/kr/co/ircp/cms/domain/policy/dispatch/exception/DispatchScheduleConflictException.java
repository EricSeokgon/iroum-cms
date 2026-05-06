package kr.co.ircp.cms.domain.policy.dispatch.exception;

/** REQ-POLICY-003: 발송 예약 상태 충돌 (PROCESSING 이후 취소 시도) */
public class DispatchScheduleConflictException extends RuntimeException {
    public DispatchScheduleConflictException(String message) {
        super(message);
    }
}
