package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-006-D-5: 본인 export 만 다운로드 가능 (SUPER_ADMIN 제외) → 403 */
public class ExportAccessDeniedException extends RuntimeException {
    public ExportAccessDeniedException(Long id) {
        super("내보내기 파일에 접근할 권한이 없습니다. id=" + id);
    }
}
