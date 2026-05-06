package kr.co.ircp.cms.domain.safety.exception;

/** REQ-SAFETY-001: 사고사례 미존재 */
public class SafetyIncidentNotFoundException extends RuntimeException {
    public SafetyIncidentNotFoundException(Long id) {
        super("사고사례를 찾을 수 없습니다. id=" + id);
    }
}
