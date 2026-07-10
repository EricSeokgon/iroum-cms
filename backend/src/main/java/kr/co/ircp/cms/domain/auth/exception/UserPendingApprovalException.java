package kr.co.ircp.cms.domain.auth.exception;

/**
 * 가입 승인 대기 상태 사용자의 로그인 시도 예외.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 REQ-UA-004 — PENDING_APPROVAL 상태 사용자는
 * 관리자 승인 전까지 로그인할 수 없다. HTTP 403 Forbidden 으로 매핑한다.
 */
public class UserPendingApprovalException extends AuthException {

    public UserPendingApprovalException() {
        super("가입 승인 대기 중입니다. 관리자 승인 후 로그인하세요.");
    }
}
