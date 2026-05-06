package kr.co.ircp.cms.domain.safety.exception;

/** REQ-SAFETY-002: 기업 안전 프로필 미존재 */
public class SafetyProfileNotFoundException extends RuntimeException {
    public SafetyProfileNotFoundException(Long companyId) {
        super("기업 안전 프로필을 찾을 수 없습니다. companyId=" + companyId);
    }
}
