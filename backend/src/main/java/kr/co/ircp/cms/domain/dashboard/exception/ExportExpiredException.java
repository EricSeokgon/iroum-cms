package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-006-D-5: export 다운로드 만료 → 410 Gone */
public class ExportExpiredException extends RuntimeException {
    public ExportExpiredException(Long id) {
        super("내보내기 파일이 만료되었습니다. id=" + id);
    }
}
