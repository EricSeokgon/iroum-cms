package kr.co.ircp.cms.domain.dashboard.kpi.service;

import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;
import kr.co.ircp.cms.domain.dashboard.exception.ExportAccessDeniedException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportExpiredException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportNotFoundException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportSignatureInvalidException;
import kr.co.ircp.cms.domain.dashboard.exception.ExportSizeLimitException;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiExportJobResponse;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiExportOutcome;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiValueResponse;
import kr.co.ircp.cms.domain.dashboard.kpi.mapper.KpiQueryMapper;
import kr.co.ircp.cms.domain.dashboard.repository.ExportHistoryMapper;
import kr.co.ircp.cms.domain.dashboard.service.ExportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SPEC-CMS-KPI-001 Phase 3: KPI Excel 내보내기 서비스 구현.
 *
 * <p>SXSSFWorkbook(윈도우 100행) 기반 대용량 OOM 방지 + 다중 시트 청킹 +
 * 동기/비동기 분기 + HMAC 서명 다운로드. PII 비노출(AC-022 동일 규칙): 집계값/차원/시각만 직렬화.
 *
 * <p>임계값은 프로퍼티로 주입한다(테스트가 작은 값으로 대용량 시나리오 재현):
 * <ul>
 *   <li>{@code iroum.kpi.export.sync-threshold}      (기본 10,000)</li>
 *   <li>{@code iroum.kpi.export.max-rows-per-sheet}  (기본 1,048,576)</li>
 *   <li>{@code iroum.kpi.export.max-export-rows}     (기본 1,000,000)</li>
 * </ul>
 *
 * // @MX:SPEC: SPEC-CMS-KPI-001 Phase 3 (AC-007/008/009/010/020)
 */
// @MX:WARN: [AUTO] writeAsyncFile — @Async 백그라운드 스레드에서 임시 파일 작성/상태 갱신
// @MX:REASON: @Async 메서드는 호출 측 트랜잭션을 승계하지 않으므로 명시적 매퍼 호출로 상태 갱신
@Service
@Transactional(readOnly = true)
public class KpiExportServiceImpl implements KpiExportService {

    private static final Logger log = LoggerFactory.getLogger(KpiExportServiceImpl.class);

    /** SXSSFWorkbook 행 윈도우(메모리 상주 행 수). */
    private static final int ROW_WINDOW = 100;

    /** 엑셀 헤더 8열 (PII 미포함: user_id/client_ip 등 제외). */
    static final String[] HEADERS = {
            "KPI Code", "KPI Name", "Period", "Dimension",
            "Value", "Unit", "Data State", "Aggregated At"
    };

    private final KpiQueryMapper kpiQueryMapper;
    private final ExportHistoryMapper historyMapper;
    private final ExportService exportService;

    private final int syncThreshold;
    private final long maxRowsPerSheet;
    private final long maxExportRows;

    public KpiExportServiceImpl(
            KpiQueryMapper kpiQueryMapper,
            ExportHistoryMapper historyMapper,
            ExportService exportService,
            @Value("${iroum.kpi.export.sync-threshold:10000}") int syncThreshold,
            @Value("${iroum.kpi.export.max-rows-per-sheet:1048576}") long maxRowsPerSheet,
            @Value("${iroum.kpi.export.max-export-rows:1000000}") long maxExportRows) {
        this.kpiQueryMapper = kpiQueryMapper;
        this.historyMapper = historyMapper;
        this.exportService = exportService;
        this.syncThreshold = syncThreshold;
        this.maxRowsPerSheet = maxRowsPerSheet;
        this.maxExportRows = maxExportRows;
    }

    // ─── 분기 판정 ────────────────────────────────────────────────────────────

    @Override
    public boolean exceedsSyncThreshold(KpiQueryRequest request) {
        return countRows(request) >= syncThreshold;
    }

    /** 조건의 전체 행 수. 상한 초과 시 ExportSizeLimitException(AC-010). */
    private long countAndGuard(KpiQueryRequest request) {
        long count = countRows(request);
        if (count > maxExportRows) {
            throw new ExportSizeLimitException(count, maxExportRows);
        }
        return count;
    }

    private long countRows(KpiQueryRequest request) {
        return kpiQueryMapper.count(request.normalize());
    }

    // ─── 통합 export 진입점 (AC-007/008/010/015) ──────────────────────────────

    // @MX:ANCHOR: [AUTO] export — KPI export 동기/비동기 분기 + 감사 캡처 진입점
    // @MX:REASON: KpiExportController 가 본 메서드를 통해 export 를 실행하고 @AuditLog 로 EXPORT 감사 적재
    @Override
    @Transactional
    @AuditLog(action = "EXPORT", entityType = "KpiExport", captureArgs = true, captureReturn = true)
    public KpiExportOutcome export(KpiQueryRequest request, Long requestorId) {
        long count = countAndGuard(request); // AC-010 상한 검사
        if (count >= syncThreshold) {
            KpiExportJobResponse job = exportAsync(request, requestorId);
            return KpiExportOutcome.async(job.jobId(), (int) count);
        }
        byte[] workbook = generateWorkbook(request);
        return KpiExportOutcome.sync((int) count, workbook);
    }

    // ─── 동기 export (AC-007) ──────────────────────────────────────────────────

    @Override
    public byte[] exportSync(KpiQueryRequest request) {
        countAndGuard(request); // AC-010 상한 검사
        return generateWorkbook(request);
    }

    // ─── 다중 시트 워크북 직렬화 (AC-009) ──────────────────────────────────────

    // @MX:ANCHOR: [AUTO] generateWorkbook — KPI export 워크북 직렬화(동기/비동기/IT 공통 진입점)
    // @MX:REASON: exportSync, writeAsyncFile, IT 가 본 메서드에 의존 (fan_in >= 3)
    @Override
    public byte[] generateWorkbook(KpiQueryRequest request) {
        List<KpiValueResponse> rows =
                kpiQueryMapper.searchForExport(request.normalize(), maxExportRows);
        try (SXSSFWorkbook wb = new SXSSFWorkbook(ROW_WINDOW);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeRows(wb, rows);
            wb.write(out);
            wb.dispose();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("KPI 엑셀 워크북 생성 실패", e);
        }
    }

    /** 헤더 + 데이터를 시트당 max_rows_per_sheet 데이터 행으로 분할 기록. */
    private void writeRows(SXSSFWorkbook wb, List<KpiValueResponse> rows) {
        int sheetIdx = 0;
        Sheet sheet = newSheet(wb, sheetIdx);
        int rowInSheet = 0; // 현재 시트의 데이터 행 수
        int physicalRow = 1; // 헤더 다음부터

        for (KpiValueResponse r : rows) {
            if (rowInSheet >= maxRowsPerSheet) {
                sheetIdx++;
                sheet = newSheet(wb, sheetIdx);
                rowInSheet = 0;
                physicalRow = 1;
            }
            writeDataRow(sheet.createRow(physicalRow), r);
            rowInSheet++;
            physicalRow++;
        }
    }

    private Sheet newSheet(SXSSFWorkbook wb, int idx) {
        Sheet sheet = wb.createSheet("KPI-" + (idx + 1));
        Row header = sheet.createRow(0);
        for (int c = 0; c < HEADERS.length; c++) {
            header.createCell(c).setCellValue(HEADERS[c]);
        }
        return sheet;
    }

    /** 단일 KPI 행 직렬화. PII 컬럼은 절대 포함하지 않는다. */
    private void writeDataRow(Row row, KpiValueResponse r) {
        cell(row, 0, r.kpiCode());
        cell(row, 1, r.kpiName());
        cell(row, 2, periodOf(r.dimensionJson()));
        cell(row, 3, r.dimensionJson());
        BigDecimal v = r.value();
        if (v != null) {
            row.createCell(4).setCellValue(v.doubleValue());
        } else {
            cell(row, 4, r.valueText());
        }
        cell(row, 5, ""); // Unit — kpi_definition 에 unit 컬럼 부재(스키마 사실)
        cell(row, 6, r.dataState());
        LocalDateTime at = r.aggregatedAt();
        cell(row, 7, at == null ? "" : at.toString());
    }

    private void cell(Row row, int idx, String value) {
        Cell c = row.createCell(idx);
        c.setCellValue(value == null ? "" : value);
    }

    /** dimension JSONB 에서 기간 키(date/week/month/quarter/year) 의 첫 값 추출(표시용). */
    private String periodOf(String dimensionJson) {
        if (dimensionJson == null) return "";
        for (String key : new String[]{"date", "week", "month", "quarter", "year"}) {
            String token = "\"" + key + "\"";
            int idx = dimensionJson.indexOf(token);
            if (idx < 0) continue;
            int colon = dimensionJson.indexOf(':', idx);
            int q1 = dimensionJson.indexOf('"', colon + 1);
            int q2 = dimensionJson.indexOf('"', q1 + 1);
            if (q1 > 0 && q2 > q1) {
                return dimensionJson.substring(q1 + 1, q2);
            }
        }
        return "";
    }

    // ─── 비동기 export (AC-008) ────────────────────────────────────────────────

    @Override
    @Transactional
    public KpiExportJobResponse exportAsync(KpiQueryRequest request, Long requestorId) {
        countAndGuard(request); // AC-010 상한 검사

        ExportHistory entry = ExportHistory.builder()
                .requestorId(requestorId)
                .exportType("EXCEL")
                .scope(scopeJson(request))
                .status(KpiExportJobResponse.PROCESSING)
                .progressPct(0)
                .build();
        historyMapper.insert(entry);

        writeAsyncFile(entry.getId(), request);
        return KpiExportJobResponse.processing(entry.getId());
    }

    /**
     * 백그라운드 파일 작성: 워크북을 임시 파일로 저장하고 export_history 를 COMPLETED 로 갱신.
     */
    @Async
    public void writeAsyncFile(Long jobId, KpiQueryRequest request) {
        try {
            byte[] workbook = generateWorkbook(request);
            Path tmp = Files.createTempFile("kpi-export-" + jobId + "-", ".xlsx");
            Files.write(tmp, workbook);

            long rowCount = countRows(request);
            ExportHistory entry = historyMapper.findById(jobId)
                    .orElseThrow(() -> new ExportNotFoundException(jobId));
            entry.setFilePath(tmp.toString());
            entry.setSizeBytes((long) workbook.length);
            entry.setRowCount((int) rowCount);
            entry.setStatus("COMPLETED");
            entry.setProgressPct(100);
            entry.setCompletedAt(Instant.now());
            historyMapper.update(entry);
        } catch (Exception e) {
            log.error("KPI 비동기 export 파일 작성 실패 jobId={}", jobId, e);
            historyMapper.findById(jobId).ifPresent(entry -> {
                entry.setStatus("FAILED");
                entry.setErrorMessage(e.getMessage());
                historyMapper.update(entry);
            });
        }
    }

    // ─── HMAC 다운로드 (AC-020) ────────────────────────────────────────────────

    @Override
    public Resource downloadExport(Long jobId, Long requestorId, String signature) {
        ExportHistory e = historyMapper.findById(jobId)
                .orElseThrow(() -> new ExportNotFoundException(jobId));

        // 만료 검사 (410 Gone)
        if (e.getExpiresAt() != null && e.getExpiresAt().isBefore(Instant.now())) {
            throw new ExportExpiredException(jobId);
        }

        // 소유권 검사 (타인 → 403)
        if (!e.getRequestorId().equals(requestorId)) {
            throw new ExportAccessDeniedException(jobId);
        }

        // 서명 검사 (위조 → 400)
        String expected = signFor(jobId, e.getExpiresAt());
        if (signature == null || !constantTimeEquals(expected, signature)) {
            throw new ExportSignatureInvalidException(jobId);
        }

        try {
            byte[] bytes = Files.readAllBytes(Path.of(e.getFilePath()));
            return new ByteArrayResource(bytes);
        } catch (Exception ex) {
            throw new ExportNotFoundException(jobId);
        }
    }

    @Override
    public String signFor(Long jobId, Instant expiresAt) {
        return exportService.signFor(jobId, expiresAt);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private String scopeJson(KpiQueryRequest request) {
        KpiQueryRequest req = request.normalize();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"kpiCode\":").append(jsonStr(req.kpiCode()));
        sb.append(",\"granularity\":").append(jsonStr(req.granularity()));
        sb.append(",\"dimensionJson\":").append(jsonStr(req.dimensionJson()));
        sb.append("}");
        return sb.toString();
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
