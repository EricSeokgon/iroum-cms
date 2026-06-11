package kr.co.ircp.cms.domain.dashboard.exception;

/**
 * Export 다운로드 HMAC 서명이 위조/손상된 경우 발생.
 *
 * <p>SPEC-CMS-KPI-001 Phase 3 AC-020 — 위조 서명은 잘못된 요청이므로 HTTP 400 으로 매핑한다.
 * 소유권 위반(타인 export 접근)은 별도로 {@link ExportAccessDeniedException}(403)으로 처리한다.
 */
// @MX:NOTE: [AUTO] ExportSignatureInvalidException — 위조 HMAC 서명(AC-020) → 400
public class ExportSignatureInvalidException extends RuntimeException {

    public ExportSignatureInvalidException(Long id) {
        super("내보내기 다운로드 서명이 올바르지 않습니다. id=" + id);
    }
}
