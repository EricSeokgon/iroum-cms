package kr.co.ircp.cms.domain.governance.controller;

import jakarta.validation.Valid;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.dto.QualityReportResponse;
import kr.co.ircp.cms.domain.governance.dto.QualityRuleRequest;
import kr.co.ircp.cms.domain.governance.dto.QualityRuleResponse;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.service.DataQualityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 데이터 품질 룰·리포트 REST 컨트롤러.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~008.
 */
// @MX:ANCHOR: [AUTO] DataQualityController — 룰 CRUD + 즉시 실행 + 리포트 (6 endpoints)
// @MX:REASON: 품질 게이트 관리 API의 단일 진입점, fan_in >= 3 (테스트, 룰 관리 UI, 모니터링)
// @MX:SPEC: SPEC-CMS-009#REQ-DATA-007
@RestController
@RequestMapping("/api/v1/governance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DataQualityController {

    private final DataQualityService service;

    // ─── 룰 관리 ──────────────────────────────────────────────────────────

    @GetMapping("/quality-rules")
    public ResponseEntity<List<QualityRuleResponse>> listRules(
            @RequestParam(required = false) String table,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String status) {
        List<QualityRuleResponse> ret = service.findRulesFiltered(table, ruleType, status).stream()
                .map(QualityRuleResponse::from).toList();
        return ResponseEntity.ok(ret);
    }

    @PostMapping("/quality-rules")
    public ResponseEntity<QualityRuleResponse> create(@Valid @RequestBody QualityRuleRequest req) {
        DataQualityRule entity = build(req, null);
        DataQualityRule created = service.createRule(entity);
        return ResponseEntity.created(URI.create("/api/v1/governance/quality-rules/" + created.getId()))
                .body(QualityRuleResponse.from(created));
    }

    @PutMapping("/quality-rules/{id}")
    public ResponseEntity<QualityRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody QualityRuleRequest req) {
        DataQualityRule entity = build(req, id);
        boolean ok = service.updateRule(entity);
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(QualityRuleResponse.from(
                service.findRuleById(id).orElseThrow()));
    }

    @DeleteMapping("/quality-rules/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (service.findRuleById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        boolean ok = service.deleteRule(id);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "code", "QUALITY_RULE_HAS_REPORTS",
                            "message", "Cannot delete rule with existing reports"));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/quality-rules/{id}/run")
    public ResponseEntity<QualityReportResponse> runRule(@PathVariable Long id) {
        DataQualityRule rule = service.findRuleById(id)
                .orElseThrow(() -> new IllegalArgumentException("data_quality_rule not found: id=" + id));
        DataQualityReport report = service.runRule(rule);
        return ResponseEntity.ok(QualityReportResponse.from(report));
    }

    // ─── 리포트 조회 ─────────────────────────────────────────────────────

    @GetMapping("/quality-reports")
    public ResponseEntity<PageResponse<QualityReportResponse>> listReports(
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) Boolean violation,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<DataQualityReport> raw = service.findReportsFiltered(ruleId, violation, severity, page, size);
        PageResponse<QualityReportResponse> mapped = PageResponse.of(
                raw.content().stream().map(QualityReportResponse::from).toList(),
                raw.page(), raw.size(), raw.totalElements());
        return ResponseEntity.ok(mapped);
    }

    private static DataQualityRule build(QualityRuleRequest r, Long id) {
        return DataQualityRule.builder()
                .id(id)
                .targetTable(r.targetTable())
                .targetColumn(r.targetColumn())
                .ruleType(r.ruleType())
                .threshold(r.threshold())
                .rangeMin(r.rangeMin())
                .rangeMax(r.rangeMax())
                .severity(r.severity() == null ? "WARN" : r.severity())
                .status(r.status() == null ? "ACTIVE" : r.status())
                .scheduleCron(r.scheduleCron() == null ? "0 0 6 * * *" : r.scheduleCron())
                .description(r.description())
                .build();
    }
}
