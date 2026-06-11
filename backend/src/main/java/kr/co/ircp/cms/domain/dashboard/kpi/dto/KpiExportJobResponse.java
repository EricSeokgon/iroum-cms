package kr.co.ircp.cms.domain.dashboard.kpi.dto;

/**
 * SPEC-CMS-KPI-001 Phase 3: 비동기 KPI export 작업 응답.
 *
 * <p>AC-008 — 행 수가 동기 임계값 이상이면 즉시 202 Accepted 와 함께
 * {jobId, status:"PROCESSING"} 를 반환하고 백그라운드에서 파일을 작성한다.
 *
 * @param jobId  export_history.id (다운로드 시 식별자)
 * @param status 작업 상태 (PROCESSING)
 */
// @MX:NOTE: [AUTO] KpiExportJobResponse — 비동기 export 작업 식별/상태 계약(AC-008)
public record KpiExportJobResponse(Long jobId, String status) {

    public static final String PROCESSING = "PROCESSING";

    public static KpiExportJobResponse processing(Long jobId) {
        return new KpiExportJobResponse(jobId, PROCESSING);
    }
}
