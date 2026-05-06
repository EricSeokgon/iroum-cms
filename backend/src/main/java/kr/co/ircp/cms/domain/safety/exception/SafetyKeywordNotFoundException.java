package kr.co.ircp.cms.domain.safety.exception;

/** REQ-SAFETY-002: 키워드 미존재 */
public class SafetyKeywordNotFoundException extends RuntimeException {
    public SafetyKeywordNotFoundException(Long id) {
        super("안전 키워드를 찾을 수 없습니다. id=" + id);
    }
}
