package kr.co.ircp.cms.domain.dashboard.kpi.service;

import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiExportJobResponse;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiExportOutcome;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import org.springframework.core.io.Resource;

import java.time.Instant;

/**
 * SPEC-CMS-KPI-001 Phase 3: KPI Excel 내보내기 서비스.
 *
 * <p>핵심 동작:
 * <ul>
 *   <li>AC-007: 행 수 &lt; sync_threshold → 동기 export (즉시 xlsx byte[] 반환).</li>
 *   <li>AC-008: 행 수 &gt;= sync_threshold → 비동기 export (202 + jobId).</li>
 *   <li>AC-009: 행 수가 max_rows_per_sheet 초과 → SXSSFWorkbook 다중 시트 분할.</li>
 *   <li>AC-010: 행 수 &gt; max_export_rows → ExportSizeLimitException(→400).</li>
 *   <li>AC-020: HMAC 서명 다운로드 (위조 400 / 타인 403).</li>
 * </ul>
 */
public interface KpiExportService {

    /**
     * 동기 export: 조건에 맞는 KPI 행을 즉시 xlsx 바이트로 직렬화한다(AC-007).
     * 행 수가 비동기 임계값 이상이면 {@link #exceedsSyncThreshold(KpiQueryRequest)} 가 true 를 반환하므로
     * 컨트롤러가 비동기 경로로 분기해야 한다. 본 메서드 자체는 상한 검사도 수행한다(AC-010).
     */
    byte[] exportSync(KpiQueryRequest request);

    /**
     * 행 수에 따라 동기/비동기를 분기하여 실행하고 결과 봉투를 반환한다(AC-007/008/010/015).
     * 컨트롤러가 본 메서드에 {@code @AuditLog} 를 적용하면 조건/행수가 감사에 캡처된다.
     */
    KpiExportOutcome export(KpiQueryRequest request, Long requestorId);

    /**
     * 비동기 export: export_history 행을 PROCESSING 으로 적재하고 백그라운드에서 파일을 작성한다(AC-008).
     *
     * @param requestorId 요청자 user id (소유권 검증 기준)
     */
    KpiExportJobResponse exportAsync(KpiQueryRequest request, Long requestorId);

    /**
     * SXSSFWorkbook 직렬화 진입점(AC-009 다중 시트 검증용 노출).
     * max_rows_per_sheet 를 초과하면 시트를 분할한다.
     */
    byte[] generateWorkbook(KpiQueryRequest request);

    /**
     * 비동기 export 파일 다운로드(AC-020). HMAC 서명·소유권·만료를 검증한 뒤 파일을 스트리밍한다.
     *
     * @throws kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException 위조 서명/타인 소유
     * @throws kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException      만료(410)
     */
    Resource downloadExport(Long jobId, Long requestorId, String signature);

    /** 조회 조건의 행 수가 동기 임계값 이상인지(비동기 분기 판정). */
    boolean exceedsSyncThreshold(KpiQueryRequest request);

    /** HMAC-SHA256 서명 생성(테스트/다운로드 URL 생성용). ExportService 위임. */
    String signFor(Long jobId, Instant expiresAt);
}
