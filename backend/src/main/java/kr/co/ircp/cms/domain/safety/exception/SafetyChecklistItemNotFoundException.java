package kr.co.ircp.cms.domain.safety.exception;

/** REQ-SAFETY-004: 체크리스트 항목 미존재 */
public class SafetyChecklistItemNotFoundException extends RuntimeException {
    public SafetyChecklistItemNotFoundException(Long id) {
        super("체크리스트 항목을 찾을 수 없습니다. id=" + id);
    }
}
