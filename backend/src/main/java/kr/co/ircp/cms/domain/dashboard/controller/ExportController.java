package kr.co.ircp.cms.domain.dashboard.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.dashboard.dto.ExportRequest;
import kr.co.ircp.cms.domain.dashboard.dto.ExportResponse;
import kr.co.ircp.cms.domain.dashboard.entity.ExportHistory;
import kr.co.ircp.cms.domain.dashboard.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Export REST 컨트롤러.
 * REQ-VIZ-006
 */
@RestController
@RequestMapping("/api/v1/dashboard/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService service;

    @PostMapping
    public ResponseEntity<ExportResponse> create(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody ExportRequest req) {
        ExportResponse resp = service.createExport(userId, req);
        // PROCESSING → 202 Accepted, COMPLETED → 200 OK
        if ("PROCESSING".equals(resp.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ExportResponse> status(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId) {
        return ResponseEntity.ok(service.getStatus(id, userId));
    }

    /** REQ-VIZ-006-D-5: chunked 다운로드 (signed URL). */
    @GetMapping("/{id}/download")
    public void download(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(value = "sig", required = false) String signature,
            @RequestParam(value = "exp", required = false) Long exp,
            HttpServletResponse response) throws java.io.IOException {
        // SUPER_ADMIN 검사는 SecurityContext / 헤더 기반에서 별도 수행 (1차: false 가정)
        ExportHistory e = service.verifyDownload(id, userId, false, signature);

        String fname = "export-" + e.getId() + "." + extension(e.getExportType());
        response.setContentType(mimeFor(e.getExportType()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fname + "\"");
        response.setHeader(HttpHeaders.TRANSFER_ENCODING, "chunked");
        // 1차 출시 stub: 빈 응답 (실제 SXSSFWorkbook write 는 v0.4+)
        if ("CSV".equals(e.getExportType())) {
            // UTF-8 BOM
            response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        response.getOutputStream().flush();
    }

    @GetMapping
    public ResponseEntity<List<ExportResponse>> history(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.listHistory(userId, status));
    }

    private String mimeFor(String exportType) {
        return switch (exportType == null ? "" : exportType) {
            case "EXCEL" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "PDF"   -> MediaType.APPLICATION_PDF_VALUE;
            default      -> "text/csv; charset=UTF-8";
        };
    }

    private String extension(String exportType) {
        return switch (exportType == null ? "" : exportType) {
            case "EXCEL" -> "xlsx";
            case "PDF"   -> "pdf";
            default      -> "csv";
        };
    }
}
