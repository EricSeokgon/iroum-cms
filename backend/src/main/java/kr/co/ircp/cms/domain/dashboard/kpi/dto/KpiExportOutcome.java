package kr.co.ircp.cms.domain.dashboard.kpi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * SPEC-CMS-KPI-001 Phase 3: export 실행 결과(감사 로그 캡처 + 응답 분기용).
 *
 * <p>AC-015 — @AuditLog(captureReturn=true) 가 본 record 를 after_value JSONB 로 직렬화하여
 * 행 수(rowCount)와 비동기 여부/jobId 를 감사에 남긴다. 워크북 바이트는 {@code @JsonIgnore}
 * 로 직렬화에서 제외하여 감사 로그 비대화를 방지한다.
 *
 * @param async    비동기 분기 여부(AC-008)
 * @param jobId    비동기 작업 식별자(동기면 null)
 * @param rowCount 내보낸 행 수(AC-015 감사 캡처 대상)
 * @param workbook 동기 export xlsx 바이트(비동기면 null, 감사 직렬화 제외)
 */
// @MX:NOTE: [AUTO] KpiExportOutcome — export 결과 봉투(감사 캡처 rowCount + 응답 분기)
public record KpiExportOutcome(
        boolean async,
        Long jobId,
        int rowCount,
        @JsonIgnore byte[] workbook
) {

    public static KpiExportOutcome sync(int rowCount, byte[] workbook) {
        return new KpiExportOutcome(false, null, rowCount, workbook);
    }

    public static KpiExportOutcome async(Long jobId, int rowCount) {
        return new KpiExportOutcome(true, jobId, rowCount, null);
    }
}
