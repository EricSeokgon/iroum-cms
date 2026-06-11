package kr.co.ircp.cms.domain.dashboard.exception;

/**
 * Export 행 수가 전체 상한(max_export_rows)을 초과할 때 발생.
 *
 * <p>SPEC-CMS-KPI-001 Phase 3 AC-010 — 전역 핸들러가 HTTP 400 Bad Request 로 매핑한다.
 */
// @MX:NOTE: [AUTO] ExportSizeLimitException — KPI export 전체 행 상한 초과(AC-010) → 400
public class ExportSizeLimitException extends RuntimeException {

    public ExportSizeLimitException(long actual, long max) {
        super("내보내기 대상 행 수(" + actual + ")가 허용 상한(" + max + ")을 초과했습니다.");
    }
}
