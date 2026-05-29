package kr.co.ircp.cms.domain.dashboard.preference.exception;

/**
 * REQ-DP-003-5 / AC-DP-003-5: 낙관적 잠금 충돌 (409 Conflict).
 *
 * <p>다른 탭/세션에서 동일 사용자의 preference 또는 layout positions 가 먼저 갱신된 경우 발생.
 * Controller 에서 HTTP 409 로 매핑된다.
 */
public class PreferenceConflictException extends RuntimeException {
    public PreferenceConflictException(String message) {
        super(message);
    }
}
