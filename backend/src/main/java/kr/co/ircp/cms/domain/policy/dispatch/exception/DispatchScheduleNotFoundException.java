package kr.co.ircp.cms.domain.policy.dispatch.exception;

/** REQ-POLICY-003: 발송 예약 미존재 */
public class DispatchScheduleNotFoundException extends RuntimeException {
    public DispatchScheduleNotFoundException(Long id) {
        super("발송 예약을 찾을 수 없습니다. id=" + id);
    }
}
