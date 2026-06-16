package kr.co.ircp.cms.domain.approval.exception;

/**
 * 승인/거절 대상 사용자가 PENDING_APPROVAL 상태가 아닐 때 발생하는 예외.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-013 — 이미 승인/거절되었거나 활성 상태인
 * 사용자에 대한 승인/거절 시도는 거부하고 HTTP 409 Conflict 를 반환한다.
 */
public class UserNotPendingApprovalException extends RuntimeException {

    public UserNotPendingApprovalException(long userId) {
        super("승인 대기 상태가 아닙니다. userId=" + userId);
    }
}
