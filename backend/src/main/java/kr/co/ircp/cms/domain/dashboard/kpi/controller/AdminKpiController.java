package kr.co.ircp.cms.domain.dashboard.kpi.controller;

import kr.co.ircp.cms.domain.audit.annotation.AuditLog;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryRequest;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiQueryResult;
import kr.co.ircp.cms.domain.dashboard.kpi.dto.KpiValueResponse;
import kr.co.ircp.cms.domain.dashboard.kpi.service.KpiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * SPEC-CMS-KPI-001 Phase 2: KPI 조회 REST 컨트롤러.
 *
 * <p>REQ-KPI-002 / REQ-KPI-005 — 클래스 레벨 {@code @PreAuthorize("hasRole('ADMIN')")} 로
 * 모든 엔드포인트를 ADMIN 전용으로 제한(AC-014). 조회 액션은 {@code @AuditLog} 로 감사 적재.
 */
// @MX:ANCHOR: [AUTO] AdminKpiController — KPI 조회 API 권한·감사 invariant
// @MX:REASON: REQ-KPI-005 ADMIN-only + AuditLog 계약. values/conversion-funnel 진입점 (fan_in 집중)
@RestController
@RequestMapping("/api/v1/admin/kpi")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminKpiController {

    private final KpiQueryService kpiQueryService;

    /**
     * AC-004/005/018/019: 동적 JSONB 필터로 KPI 집계값 조회.
     * size 는 서비스에서 1000 으로 강제 클램프(AC-019).
     */
    @GetMapping("/values")
    @AuditLog(action = "READ", entityType = "KpiValue")
    public ResponseEntity<KpiQueryResult> values(
            @RequestParam(required = false) String kpiCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String dimensionJson,
            @RequestParam(required = false) String granularity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        KpiQueryRequest req = new KpiQueryRequest(
                kpiCode, fromDate, toDate, dimensionJson, granularity, page, size);
        return ResponseEntity.ok(kpiQueryService.query(req));
    }

    /**
     * AC-006: 정책 매칭 전환율 퍼널. 데이터 부재 시 dataState=PREPARING.
     */
    @GetMapping("/conversion-funnel")
    @AuditLog(action = "READ", entityType = "KpiConversionFunnel")
    public ResponseEntity<KpiValueResponse> conversionFunnel(
            @RequestParam(required = false) String statMonth) {
        return ResponseEntity.ok(kpiQueryService.conversionFunnel(statMonth));
    }
}
