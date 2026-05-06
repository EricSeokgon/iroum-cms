package kr.co.ircp.cms.domain.safety.controller;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.safety.dto.CheckResultRequest;
import kr.co.ircp.cms.domain.safety.dto.CheckResultResponse;
import kr.co.ircp.cms.domain.safety.dto.ChecklistStatsResponse;
import kr.co.ircp.cms.domain.safety.dto.ReportCreateRequest;
import kr.co.ircp.cms.domain.safety.dto.ReportDetail;
import kr.co.ircp.cms.domain.safety.dto.ReportSummary;
import kr.co.ircp.cms.domain.safety.service.SafetyChecklistService;
import kr.co.ircp.cms.domain.safety.service.SafetyGuidelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 보고서 + 체크리스트 REST 컨트롤러.
 * REQ-SAFETY-003 + REQ-SAFETY-004
 */
@RestController
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
public class SafetyReportController {

    private final SafetyGuidelineService guidelineService;
    private final SafetyChecklistService checklistService;

    /** POST /api/v1/safety/reports — 본인 회사 가이드라인 보고서 생성. */
    @PostMapping("/reports")
    public ResponseEntity<ReportDetail> generate(
            @RequestBody(required = false) ReportCreateRequest request,
            @AuthenticationPrincipal Long companyId) {
        ReportDetail created = guidelineService.generateReport(companyId, request);
        return ResponseEntity.created(URI.create("/api/v1/safety/reports/" + created.uuid())).body(created);
    }

    /** GET /api/v1/safety/reports/{uuid} — 본인 또는 관리자만. */
    @GetMapping("/reports/{uuid}")
    public ResponseEntity<ReportDetail> getReport(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal Long companyId) {
        return ResponseEntity.ok(guidelineService.getReport(uuid, isAdmin(), companyId));
    }

    /** GET /api/v1/safety/reports/{uuid}/pdf — PDF 다운로드 경로. */
    @GetMapping("/reports/{uuid}/pdf")
    public ResponseEntity<String> getPdfPath(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal Long companyId) {
        String path = guidelineService.getReportPdfPath(uuid, isAdmin(), companyId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                .body(path == null ? "" : path);
    }

    /** GET /api/v1/safety/reports/me */
    @GetMapping("/reports/me")
    public ResponseEntity<PageResponse<ReportSummary>> myReports(
            @AuthenticationPrincipal Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(guidelineService.listMyReports(companyId, page, size));
    }

    /** GET /api/v1/safety/admin/reports */
    @GetMapping("/admin/reports")
    public ResponseEntity<PageResponse<ReportSummary>> allReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(guidelineService.listAllReports(page, size));
    }

    // ─── 체크리스트 ──────────────────────────────────────────────────────────

    /** GET /api/v1/safety/reports/{uuid}/checklist */
    @GetMapping("/reports/{uuid}/checklist")
    public ResponseEntity<List<CheckResultResponse>> checklist(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal Long companyId) {
        return ResponseEntity.ok(checklistService.getChecklistByReport(uuid, isAdmin(), companyId));
    }

    /** PUT /api/v1/safety/reports/{uuid}/checklist/{itemId} */
    @PutMapping("/reports/{uuid}/checklist/{itemId}")
    public ResponseEntity<CheckResultResponse> upsertCheckResult(
            @PathVariable UUID uuid,
            @PathVariable Long itemId,
            @Valid @RequestBody CheckResultRequest request,
            @AuthenticationPrincipal Long companyId) {
        return ResponseEntity.ok(checklistService.upsertCheckResult(
                uuid, itemId, request, companyId, isAdmin(), companyId));
    }

    /** GET /api/v1/safety/admin/checklist/stats */
    @GetMapping("/admin/checklist/stats")
    public ResponseEntity<ChecklistStatsResponse> stats() {
        return ResponseEntity.ok(checklistService.getOverallStats());
    }

    /** SecurityContext에서 SUPER_ADMIN/DEPT_ADMIN 권한 보유 여부. */
    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String role = a.getAuthority();
            return "ROLE_SUPER_ADMIN".equals(role) || "ROLE_DEPT_ADMIN".equals(role);
        });
    }
}
