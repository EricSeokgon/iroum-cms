package kr.co.ircp.cms.domain.safety.exception;

/** REQ-SAFETY-005: 가이드라인 템플릿 미존재 */
public class SafetyTemplateNotFoundException extends RuntimeException {
    public SafetyTemplateNotFoundException(String message) {
        super(message);
    }
    public SafetyTemplateNotFoundException(Long id) {
        super("가이드라인 템플릿을 찾을 수 없습니다. id=" + id);
    }
}
