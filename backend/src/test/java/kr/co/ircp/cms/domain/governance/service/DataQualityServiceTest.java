package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.audit.notification.CriticalAuditNotifier;
import kr.co.ircp.cms.domain.audit.service.AuditLogService;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.quality.QualityCheckResult;
import kr.co.ircp.cms.domain.governance.quality.QualityChecker;
import kr.co.ircp.cms.domain.governance.quality.QualityCheckerRegistry;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataQualityService GREEN 단계 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-006~008 — 룰 CRUD + 즉시 실행 + 리포트 페이징 조회 + CRITICAL 알림.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataQualityService GREEN 테스트 (REQ-DATA-006~008)")
class DataQualityServiceTest {

    @Mock private DataQualityMapper mapper;
    @Mock private QualityCheckerRegistry registry;
    @Mock private JdbcTemplate jdbc;
    @Mock private CriticalAuditNotifier criticalNotifier;
    @Mock private QualityChecker checker;

    private DataQualityService service;

    @BeforeEach
    void setUp() {
        service = new DataQualityService(mapper, registry, jdbc, criticalNotifier);
    }

    // ─── 공통 스텁 빌더 ─────────────────────────────────────────────────────

    private DataQualityRule stubRule(long id, String type, String severity) {
        return DataQualityRule.builder()
                .id(id)
                .targetTable("users")
                .targetColumn("email")
                .ruleType(type)
                .threshold(new BigDecimal("0.05"))
                .severity(severity)
                .status("ACTIVE")
                .description("규칙 " + id)
                .build();
    }

    // ──────────────────────────────────────────────
    // findRuleById
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findRuleById — mapper 위임")
    void findRuleById_delegatesToMapper() {
        DataQualityRule rule = stubRule(1L, "NULL_RATIO", "WARN");
        when(mapper.findRuleById(1L)).thenReturn(Optional.of(rule));

        Optional<DataQualityRule> result = service.findRuleById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getRuleType()).isEqualTo("NULL_RATIO");
        verify(mapper).findRuleById(1L);
    }

    @Test
    @DisplayName("findRuleById — 미존재 ID는 Optional.empty")
    void findRuleById_nonExistent_returnsEmpty() {
        when(mapper.findRuleById(999L)).thenReturn(Optional.empty());

        Optional<DataQualityRule> result = service.findRuleById(999L);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    // findRulesFiltered
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findRulesFiltered — params Map에 targetTable/ruleType/status 매핑")
    void findRulesFiltered_passesParams() {
        when(mapper.findRulesFiltered(any())).thenReturn(List.of(stubRule(1L, "NULL_RATIO", "WARN")));

        List<DataQualityRule> result = service.findRulesFiltered("users", "NULL_RATIO", "ACTIVE");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findRulesFiltered(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("targetTable", "users");
        assertThat(params).containsEntry("ruleType", "NULL_RATIO");
        assertThat(params).containsEntry("status", "ACTIVE");
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findRulesFiltered — null 인자도 그대로 Map에 매핑")
    void findRulesFiltered_nullParams_safe() {
        when(mapper.findRulesFiltered(any())).thenReturn(List.of());

        service.findRulesFiltered(null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findRulesFiltered(captor.capture());
        assertThat(captor.getValue()).containsKeys("targetTable", "ruleType", "status");
        assertThat(captor.getValue().get("targetTable")).isNull();
    }

    // ──────────────────────────────────────────────
    // findReportsFiltered
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("findReportsFiltered — content + total → PageResponse 반환")
    void findReportsFiltered_returnsPageResponse() {
        DataQualityReport rep = DataQualityReport.builder().id(100L).ruleId(1L).violation(false).build();
        when(mapper.findReportsFiltered(any())).thenReturn(List.of(rep));
        when(mapper.countReportsFiltered(any())).thenReturn(1);

        PageResponse<DataQualityReport> result = service.findReportsFiltered(
                1L, false, "WARN", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mapper).findReportsFiltered(captor.capture());
        Map<String, Object> params = captor.getValue();
        assertThat(params).containsEntry("ruleId", 1L);
        assertThat(params).containsEntry("violation", false);
        assertThat(params).containsEntry("severity", "WARN");
        assertThat(params).containsEntry("offset", 0);
        assertThat(params).containsEntry("size", 20);
    }

    // ──────────────────────────────────────────────
    // createRule
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("createRule — mapper.insertRule 호출 후 동일 객체 반환")
    void createRule_callsInsertAndReturns() {
        DataQualityRule input = stubRule(0L, "RANGE", "INFO");

        DataQualityRule result = service.createRule(input);

        verify(mapper).insertRule(input);
        assertThat(result).isSameAs(input);
    }

    // ──────────────────────────────────────────────
    // updateRule
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("updateRule — affected rows > 0이면 true 반환")
    void updateRule_affectedRows_returnsTrue() {
        DataQualityRule input = stubRule(1L, "NULL_RATIO", "WARN");
        when(mapper.updateRule(input)).thenReturn(1);

        boolean result = service.updateRule(input);

        assertThat(result).isTrue();
        verify(mapper).updateRule(input);
    }

    @Test
    @DisplayName("updateRule — affected rows = 0이면 false 반환")
    void updateRule_zeroAffected_returnsFalse() {
        DataQualityRule input = stubRule(99L, "NULL_RATIO", "WARN");
        when(mapper.updateRule(input)).thenReturn(0);

        boolean result = service.updateRule(input);

        assertThat(result).isFalse();
    }

    // ──────────────────────────────────────────────
    // deleteRule
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("deleteRule — 연결된 리포트 0건이면 mapper.deleteRule 호출, 영향행 1 → true")
    void deleteRule_noReports_deletesAndReturnsTrue() {
        when(mapper.countReportsByRuleId(1L)).thenReturn(0);
        when(mapper.deleteRule(1L)).thenReturn(1);

        boolean result = service.deleteRule(1L);

        assertThat(result).isTrue();
        verify(mapper).deleteRule(1L);
    }

    @Test
    @DisplayName("deleteRule — 연결된 리포트 존재 시 false 반환 + deleteRule 미호출")
    void deleteRule_withReports_returnsFalseAndSkipsDelete() {
        when(mapper.countReportsByRuleId(1L)).thenReturn(3);

        boolean result = service.deleteRule(1L);

        assertThat(result).isFalse();
        verify(mapper, never()).deleteRule(any());
    }

    @Test
    @DisplayName("deleteRule — countReports=0이지만 mapper.deleteRule이 0행 → false 반환")
    void deleteRule_zeroAffected_returnsFalse() {
        when(mapper.countReportsByRuleId(1L)).thenReturn(0);
        when(mapper.deleteRule(1L)).thenReturn(0);

        boolean result = service.deleteRule(1L);

        assertThat(result).isFalse();
    }

    // ──────────────────────────────────────────────
    // runRule — checker dispatch + report 적재 + 알림
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("runRule — checker가 violation=false 반환 시 리포트 적재 + 알림 미호출")
    void runRule_noViolation_insertsReportNoNotification() {
        DataQualityRule rule = stubRule(1L, "NULL_RATIO", "WARN");
        QualityCheckResult okResult = new QualityCheckResult(new BigDecimal("0.01"), false, "ok");
        when(registry.forType("NULL_RATIO")).thenReturn(checker);
        when(checker.check(rule, jdbc)).thenReturn(okResult);

        DataQualityReport result = service.runRule(rule);

        ArgumentCaptor<DataQualityReport> captor = ArgumentCaptor.forClass(DataQualityReport.class);
        verify(mapper).insertReport(captor.capture());
        DataQualityReport inserted = captor.getValue();
        assertThat(inserted.getRuleId()).isEqualTo(1L);
        assertThat(inserted.getViolation()).isFalse();
        assertThat(inserted.getMeasuredValue()).isEqualByComparingTo("0.01");
        assertThat(inserted.getNotified()).isFalse();
        assertThat(inserted.getCheckedAt()).isNotNull();

        verify(criticalNotifier, never()).enqueue(any());
        verify(mapper, never()).updateReportNotified(any());
        assertThat(result).isSameAs(inserted);
    }

    @Test
    @DisplayName("runRule — CRITICAL severity + violation=true → CriticalAuditNotifier.enqueue + updateReportNotified 호출")
    void runRule_criticalViolation_enqueuesAndMarksNotified() {
        DataQualityRule rule = stubRule(2L, "NULL_RATIO", "CRITICAL");
        QualityCheckResult bad = new QualityCheckResult(new BigDecimal("0.30"), true, "30% null");
        when(registry.forType("NULL_RATIO")).thenReturn(checker);
        when(checker.check(rule, jdbc)).thenReturn(bad);

        // mapper.insertReport가 id를 채워주도록 모킹 (updateReportNotified가 id 사용)
        org.mockito.Mockito.doAnswer(invocation -> {
            DataQualityReport r = invocation.getArgument(0);
            // Builder 기반 — setter 없음. id 없이도 검증 가능 (null 전달 허용)
            return null;
        }).when(mapper).insertReport(any(DataQualityReport.class));

        service.runRule(rule);

        // CRITICAL 알림 발송 검증
        ArgumentCaptor<AuditLogService.AuditLogRecord> notifyCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogRecord.class);
        verify(criticalNotifier).enqueue(notifyCaptor.capture());
        AuditLogService.AuditLogRecord record = notifyCaptor.getValue();
        assertThat(record.severity()).isEqualTo("CRITICAL");
        assertThat(record.action()).isEqualTo("DATA_QUALITY_VIOLATION");
        assertThat(record.entityType()).isEqualTo("data_quality_rule");
        assertThat(record.entityId()).isEqualTo("2");
        assertThat(record.result()).isEqualTo("FAILURE");

        // updateReportNotified 호출됨 (id는 null이지만 호출 자체는 발생)
        verify(mapper).updateReportNotified(any());
    }

    @Test
    @DisplayName("runRule — WARN severity + violation=true → notifier 미호출 + updateReportNotified만 호출")
    void runRule_warnViolation_logsAndMarksNotified() {
        DataQualityRule rule = stubRule(3L, "RANGE", "WARN");
        QualityCheckResult bad = new QualityCheckResult(new BigDecimal("0.15"), true, "out of range");
        when(registry.forType("RANGE")).thenReturn(checker);
        when(checker.check(rule, jdbc)).thenReturn(bad);

        service.runRule(rule);

        verify(mapper).insertReport(any(DataQualityReport.class));
        verify(criticalNotifier, never()).enqueue(any());
        verify(mapper).updateReportNotified(any());
    }

    @Test
    @DisplayName("runRule — INFO severity + violation=true → 알림/업데이트 모두 미호출 (no-op)")
    void runRule_infoViolation_noOp() {
        DataQualityRule rule = stubRule(4L, "FRESHNESS", "INFO");
        QualityCheckResult bad = new QualityCheckResult(new BigDecimal("48"), true, "stale");
        when(registry.forType("FRESHNESS")).thenReturn(checker);
        when(checker.check(rule, jdbc)).thenReturn(bad);

        service.runRule(rule);

        verify(mapper).insertReport(any(DataQualityReport.class));
        verify(criticalNotifier, never()).enqueue(any());
        verify(mapper, never()).updateReportNotified(any());
    }

    @Test
    @DisplayName("runRule — violation=true이지만 severity=null이면 알림 미호출")
    void runRule_violationButNullSeverity_noNotification() {
        DataQualityRule rule = DataQualityRule.builder()
                .id(5L).ruleType("UNIQUE").targetTable("users").targetColumn("email")
                .severity(null).status("ACTIVE").build();
        QualityCheckResult bad = new QualityCheckResult(new BigDecimal("3"), true, "duplicates");
        when(registry.forType("UNIQUE")).thenReturn(checker);
        when(checker.check(rule, jdbc)).thenReturn(bad);

        service.runRule(rule);

        verify(mapper).insertReport(any(DataQualityReport.class));
        verify(criticalNotifier, never()).enqueue(any());
        verify(mapper, never()).updateReportNotified(any());
    }
}
