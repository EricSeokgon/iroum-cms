package kr.co.ircp.cms.domain.dashboard.exception;

/** REQ-VIZ-004: 저장된 뷰 미존재 → 404 */
public class SavedViewNotFoundException extends RuntimeException {
    public SavedViewNotFoundException(Long id) {
        super("저장된 뷰를 찾을 수 없습니다. id=" + id);
    }
}
