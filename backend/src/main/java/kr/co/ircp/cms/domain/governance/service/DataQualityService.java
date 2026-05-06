package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.audit.notification.CriticalAuditNotifier;
import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.quality.QualityCheckResult;
import kr.co.ircp.cms.domain.governance.quality.QualityCheckerRegistry;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 데이터 품질 룰·리포트 서비스.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~008 — 룰 CRUD + 즉시 실행 + 리포트 조회 + 위반 알림.
 */
// @MX:ANCHOR: [AUTO] DataQualityService — DataQualityCheckJob + DataQualityController 공통 진입점 (fan_in >= 3)
// @MX:REASON: 품질 룰 dispatch + 리포트 적재 + CRITICAL 알림 트리거의 단일 책임
// @MX:SPEC: SPEC-CMS-009#REQ-DATA-007
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataQualityService {

    private final DataQualityMapper mapper;
    private final QualityCheckerRegistry registry;
    private final JdbcTemplate jdbc;
    private final CriticalAuditNotifier criticalNotifier;

    public Optional<DataQualityRule> findRuleById(Long id) {
        return mapper.findRuleById(id);
    }

    public List<DataQualityRule> findRulesFiltered(String targetTable, String ruleType, String status) {
        Map<String, Object> p = new HashMap<>();
        p.put("targetTable", targetTable);
        p.put("ruleType", ruleType);
        p.put("status", status);
        return mapper.findRulesFiltered(p);
    }

    public PageResponse<DataQualityReport> findReportsFiltered(Long ruleId,
                                                                 Boolean violation,
                                                                 String severity,
                                                                 int page,
                                                                 int size) {
        Map<String, Object> p = new HashMap<>();
        p.put("ruleId", ruleId);
        p.put("violation", violation);
        p.put("severity", severity);
        p.put("offset", page * size);
        p.put("size", size);

        List<DataQualityReport> content = mapper.findReportsFiltered(p);
        long total = mapper.countReportsFiltered(p);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional
    public DataQualityRule createRule(DataQualityRule rule) {
        mapper.insertRule(rule);
        return rule;
    }

    @Transactional
    public boolean updateRule(DataQualityRule rule) {
        return mapper.updateRule(rule) > 0;
    }

    /**
     * 룰 삭제. 연결된 리포트가 존재하면 false 반환 (컨트롤러는 409로 매핑).
     */
    @Transactional
    public boolean deleteRule(Long id) {
        if (mapper.countReportsByRuleId(id) > 0) {
            return false;
        }
        return mapper.deleteRule(id) > 0;
    }

    /**
     * 단일 룰 즉시 실행 — 새 리포트 INSERT.
     *
     * <p>룰 타입에 맞는 checker를 찾아 실행, 결과를 data_quality_report에 저장.
     * 위반 + severity in (WARN/CRITICAL) 인 경우 알림 처리.
     */
    @Transactional
    public DataQualityReport runRule(DataQualityRule rule) {
        QualityCheckResult result = registry.forType(rule.getRuleType()).check(rule, jdbc);

        DataQualityReport report = DataQualityReport.builder()
                .ruleId(rule.getId())
                .checkedAt(Instant.now())
                .measuredValue(result.measuredValue())
                .violation(result.violation())
                .detail(result.detail())
                .notified(false)
                .build();
        mapper.insertReport(report);

        if (result.violation() && rule.getSeverity() != null) {
            handleViolation(rule, report);
        }
        return report;
    }

    /**
     * 위반 알림 처리.
     * - CRITICAL: CriticalAuditNotifier에 enqueue (severity=CRITICAL이면 push, 아니면 무시)
     * - WARN:    SLF4J warn 로깅
     * - INFO:    no-op
     */
    private void handleViolation(DataQualityRule rule, DataQualityReport report) {
        String severity = rule.getSeverity();
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            criticalNotifier.enqueue(buildCriticalRecord(rule, report));
            mapper.updateReportNotified(report.getId());
            log.error("CRITICAL data quality violation: rule_id={} table={} measured={} threshold={}",
                    rule.getId(), rule.getTargetTable(), report.getMeasuredValue(), rule.getThreshold());
        } else if ("WARN".equalsIgnoreCase(severity)) {
            log.warn("WARN data quality violation: rule_id={} table={} measured={} threshold={}",
                    rule.getId(), rule.getTargetTable(), report.getMeasuredValue(), rule.getThreshold());
            mapper.updateReportNotified(report.getId());
        }
    }

    private AuditLogService.AuditLogRecord buildCriticalRecord(DataQualityRule rule, DataQualityReport report) {
        return new AuditLogService.AuditLogRecord(
                Instant.now(),
                /* actorId */ null,
                /* actorRole */ "SYSTEM",
                /* action */ "DATA_QUALITY_VIOLATION",
                /* entityType */ "data_quality_rule",
                /* entityId */ String.valueOf(rule.getId()),
                /* beforeValue */ null,
                /* afterValue */ report.getDetail(),
                /* ipAddress */ null,
                /* userAgent */ null,
                /* traceId */ null,
                /* severity */ "CRITICAL",
                /* result */ "FAILURE",
                /* failureReason */
                "table=" + rule.getTargetTable()
                        + " measured=" + report.getMeasuredValue()
                        + " threshold=" + rule.getThreshold(),
                /* durationMs */ null
        );
    }
}
