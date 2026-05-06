package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-006: export 이력 미존재 → 404 */
public class ExportNotFoundException extends RuntimeException {
    public ExportNotFoundException(Long id) {
        super("내보내기 이력을 찾을 수 없습니다. id=" + id);
    }
}
