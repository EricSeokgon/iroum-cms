package kr.co.ircp.cms.domain.safety.exception;

import java.util.UUID;

/** REQ-SAFETY-003: 가이드라인 보고서 미존재 */
public class SafetyReportNotFoundException extends RuntimeException {
    public SafetyReportNotFoundException(UUID uuid) {
        super("가이드라인 보고서를 찾을 수 없습니다. uuid=" + uuid);
    }
    public SafetyReportNotFoundException(Long id) {
        super("가이드라인 보고서를 찾을 수 없습니다. id=" + id);
    }
}
