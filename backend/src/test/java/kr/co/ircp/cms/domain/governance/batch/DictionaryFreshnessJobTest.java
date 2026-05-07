package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.DataDictionary;
import kr.co.ircp.cms.domain.governance.entity.DataQualityReport;
import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.repository.DataDictionaryMapper;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DictionaryFreshnessJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-005 — information_schema.columns ↔ data_dictionary 차이 검증.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DictionaryFreshnessJob — 사전 현행화 검증 (REQ-GOV-005)")
class DictionaryFreshnessJobTest {

    @Mock private DataDictionaryMapper dictionaryMapper;
    @Mock private DataQualityMapper qualityMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private DictionaryFreshnessJob job;

    private DataDictionary dict(String table, String column, String status) {
        return DataDictionary.builder()
                .id(1L).tableName(table).columnName(column).status(status)
                .logicalNameKo("로지컬").dataType("varchar").isPii(false).isRequired(true)
                .build();
    }

    private DataDictionaryMapper.SchemaColumn schemaCol(String table, String column) {
        DataDictionaryMapper.SchemaColumn c = new DataDictionaryMapper.SchemaColumn();
        c.setTableName(table);
        c.setColumnName(column);
        return c;
    }

    private DataQualityRule freshnessRule() {
        return DataQualityRule.builder()
                .id(99L)
                .targetTable("data_dictionary")
                .targetColumn(null)
                .ruleType("FRESHNESS")
                .threshold(BigDecimal.valueOf(24))
                .severity("WARN")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run — FRESHNESS 룰이 없으면 0 반환 + insertReport 미호출")
    void run_noFreshnessRule_returnsZero() {
        when(dictionaryMapper.findAll()).thenReturn(List.of());
        when(dictionaryMapper.findActualSchemaColumns()).thenReturn(List.of());
        when(qualityMapper.findActiveRules()).thenReturn(List.of()); // FRESHNESS 룰 없음

        int reported = job.run();

        assertThat(reported).isEqualTo(0);
        verify(qualityMapper, never()).insertReport(any());
    }

    @Test
    @DisplayName("run — actual에는 있고 registered에는 없으면 MISSING_IN_DICTIONARY 적재")
    void run_missingInDictionary_reportsMissing() {
        // registered: 비어 있음
        when(dictionaryMapper.findAll()).thenReturn(List.of());
        // actual: users.email 존재
        when(dictionaryMapper.findActualSchemaColumns())
                .thenReturn(List.of(schemaCol("users", "email")));
        when(qualityMapper.findActiveRules()).thenReturn(List.of(freshnessRule()));

        int reported = job.run();

        assertThat(reported).isEqualTo(1);
        ArgumentCaptor<DataQualityReport> captor = ArgumentCaptor.forClass(DataQualityReport.class);
        verify(qualityMapper, times(1)).insertReport(captor.capture());
        DataQualityReport r = captor.getValue();
        assertThat(r.getRuleId()).isEqualTo(99L);
        assertThat(r.getViolation()).isTrue();
        assertThat(r.getDetail()).startsWith("MISSING_IN_DICTIONARY:");
        assertThat(r.getDetail()).contains("users.email");
    }

    @Test
    @DisplayName("run — registered에 있고 actual에는 없으면 STALE_IN_DICTIONARY 적재")
    void run_staleInDictionary_reportsStale() {
        when(dictionaryMapper.findAll())
                .thenReturn(List.of(dict("legacy", "removed_col", "ACTIVE")));
        when(dictionaryMapper.findActualSchemaColumns()).thenReturn(List.of());
        when(qualityMapper.findActiveRules()).thenReturn(List.of(freshnessRule()));

        int reported = job.run();

        assertThat(reported).isEqualTo(1);
        ArgumentCaptor<DataQualityReport> captor = ArgumentCaptor.forClass(DataQualityReport.class);
        verify(qualityMapper).insertReport(captor.capture());
        assertThat(captor.getValue().getDetail()).startsWith("STALE_IN_DICTIONARY:");
        assertThat(captor.getValue().getDetail()).contains("legacy.removed_col");
    }

    @Test
    @DisplayName("run — REMOVED 상태 dictionary 항목은 registered에서 제외")
    void run_removedStatus_excludedFromRegistered() {
        // REMOVED 상태는 registered에 포함되지 않으므로 actual의 같은 키도 MISSING으로 보고됨
        when(dictionaryMapper.findAll())
                .thenReturn(List.of(dict("users", "email", "REMOVED")));
        when(dictionaryMapper.findActualSchemaColumns())
                .thenReturn(List.of(schemaCol("users", "email")));
        when(qualityMapper.findActiveRules()).thenReturn(List.of(freshnessRule()));

        int reported = job.run();

        assertThat(reported).isEqualTo(1);
        ArgumentCaptor<DataQualityReport> captor = ArgumentCaptor.forClass(DataQualityReport.class);
        verify(qualityMapper).insertReport(captor.capture());
        assertThat(captor.getValue().getDetail()).startsWith("MISSING_IN_DICTIONARY:");
    }

    @Test
    @DisplayName("run — 일치하는 항목만 있으면 0 반환")
    void run_allMatch_returnsZero() {
        when(dictionaryMapper.findAll())
                .thenReturn(List.of(dict("users", "email", "ACTIVE")));
        when(dictionaryMapper.findActualSchemaColumns())
                .thenReturn(List.of(schemaCol("users", "email")));
        when(qualityMapper.findActiveRules()).thenReturn(List.of(freshnessRule()));

        int reported = job.run();

        assertThat(reported).isEqualTo(0);
        verify(qualityMapper, never()).insertReport(any());
    }

    @Test
    @DisplayName("run — MISSING과 STALE 둘 다 적재되는 케이스")
    void run_mixedMissingAndStale() {
        when(dictionaryMapper.findAll())
                .thenReturn(List.of(dict("legacy", "old_col", "ACTIVE"))); // STALE 후보
        when(dictionaryMapper.findActualSchemaColumns())
                .thenReturn(List.of(schemaCol("users", "email"))); // MISSING 후보
        when(qualityMapper.findActiveRules()).thenReturn(List.of(freshnessRule()));

        int reported = job.run();

        assertThat(reported).isEqualTo(2);
        verify(qualityMapper, atLeast(2)).insertReport(any());
    }

    @Test
    @DisplayName("run — 다수의 FRESHNESS 룰 중 data_dictionary 룰만 우선 사용")
    void run_picksDataDictionaryFreshnessRule() {
        DataQualityRule otherRule = DataQualityRule.builder()
                .id(50L).targetTable("orders").ruleType("FRESHNESS")
                .threshold(BigDecimal.ONE).severity("WARN").status("ACTIVE").build();
        DataQualityRule targetRule = freshnessRule();
        when(dictionaryMapper.findAll()).thenReturn(List.of());
        when(dictionaryMapper.findActualSchemaColumns())
                .thenReturn(List.of(schemaCol("users", "email")));
        // 순서: 다른 룰 → 타겟 룰 → 첫 매칭이 우선
        List<DataQualityRule> rules = new ArrayList<>();
        rules.add(otherRule);
        rules.add(targetRule);
        when(qualityMapper.findActiveRules()).thenReturn(rules);

        int reported = job.run();

        assertThat(reported).isEqualTo(1);
        ArgumentCaptor<DataQualityReport> captor = ArgumentCaptor.forClass(DataQualityReport.class);
        verify(qualityMapper).insertReport(captor.capture());
        assertThat(captor.getValue().getRuleId()).isEqualTo(99L);
    }
}
