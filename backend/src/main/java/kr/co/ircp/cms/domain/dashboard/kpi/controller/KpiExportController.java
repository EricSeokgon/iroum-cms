package kr.co.ircp.cms.domain.dashboard.kpi.controller;

import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiExportJobResponse;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiExportOutcome;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import kr.co.ircp.cms.domain.dashboard.kpi.service.KpiExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * SPEC-CMS-KPI-001 Phase 3: KPI Excel 내보내기 REST 컨트롤러.
 *
 * <p>클래스 레벨 {@code @PreAuthorize("hasRole('ADMIN')")} 로 ADMIN 전용(AC-014).
 * export 실행은 서비스 {@code @AuditLog(action="EXPORT")} 로 감사 적재(AC-015).
 *
 * <ul>
 *   <li>POST /api/v1/admin/kpi/export — 동기(200+xlsx) 또는 비동기(202+jobId) 분기</li>
 *   <li>GET  /api/v1/admin/kpi/export/download — HMAC 서명 다운로드</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] KpiExportController — KPI export API 권한·감사·HMAC 다운로드 invariant
// @MX:REASON: REQ-KPI ADMIN-only + EXPORT 감사 + signed download 계약 (운영 진입점)
@RestController
@RequestMapping("/api/v1/admin/kpi/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KpiExportController {

    /** xlsx MIME. */
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final KpiExportService kpiExportService;

    /**
     * KPI export 실행. 행 수 &lt; sync_threshold → 200 + xlsx(AC-007),
     * &gt;= threshold → 202 + {jobId, status}(AC-008), &gt; max_export_rows → 400(AC-010).
     */
    @PostMapping
    public ResponseEntity<?> export(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String kpiCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String dimensionJson,
            @RequestParam(required = false) String granularity) {
        KpiQueryRequest req = new KpiQueryRequest(
                kpiCode, fromDate, toDate, dimensionJson, granularity, 0, 0);

        KpiExportOutcome outcome = kpiExportService.export(req, principal.userId());

        if (outcome.async()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(KpiExportJobResponse.processing(outcome.jobId()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kpi-export.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(outcome.workbook());
    }

    /**
     * 비동기 export 파일 다운로드(AC-020). HMAC 서명 검증 후 xlsx 스트리밍.
     * 위조 서명 → 400, 타인 소유 → 403, 만료 → 410.
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam Long jobId,
            @RequestParam(value = "sig", required = false) String signature,
            @RequestParam(value = "exp", required = false) Long exp) {
        Resource resource = kpiExportService.downloadExport(jobId, principal.userId(), signature);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"kpi-export-" + jobId + ".xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(resource);
    }
}
