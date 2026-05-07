package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.entity.DataQualityRule;
import kr.co.ircp.cms.domain.governance.repository.DataQualityMapper;
import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import kr.co.ircp.cms.domain.governance.service.DataQualityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DataQualityCheckJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-007 — 활성 룰 dispatch + graceful skip + 예외 처리.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataQualityCheckJob — 활성 룰 dispatch (REQ-DATA-007)")
class DataQualityCheckJobTest {

    @Mock private DataQualityMapper qualityMapper;
    @Mock private GovernanceStatsMapper statsMapper;
    @Mock private DataQualityService qualityService;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private DataQualityCheckJob job;

    private DataQualityRule rule(long id, String table) {
        return DataQualityRule.builder()
                .id(id)
                .targetTable(table)
                .targetColumn("email")
                .ruleType("NULL_RATIO")
                .threshold(new BigDecimal("0.05"))
                .severity("WARN")
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("run — 활성 룰 모두 dispatch (테이블 존재)")
    void run_dispatchesAllRules() {
        DataQualityRule r1 = rule(1L, "users");
        DataQualityRule r2 = rule(2L, "orders");
        when(qualityMapper.findActiveRules()).thenReturn(List.of(r1, r2));
        when(statsMapper.countTable("users")).thenReturn(1);
        when(statsMapper.countTable("orders")).thenReturn(1);

        int processed = job.run();

        assertThat(processed).isEqualTo(2);
        verify(qualityService).runRule(r1);
        verify(qualityService).runRule(r2);
    }

    @Test
    @DisplayName("run — 테이블 미존재 룰은 SKIP 카운트 미반영 (graceful)")
    void run_missingTable_skipped() {
        DataQualityRule r1 = rule(1L, "users");
        DataQualityRule r2 = rule(2L, "missing_table");
        when(qualityMapper.findActiveRules()).thenReturn(List.of(r1, r2));
        when(statsMapper.countTable("users")).thenReturn(1);
        when(statsMapper.countTable("missing_table")).thenReturn(0);

        int processed = job.run();

        assertThat(processed).isEqualTo(1);
        verify(qualityService).runRule(r1);
        verify(qualityService, never()).runRule(r2);
    }

    @Test
    @DisplayName("run — runRule 예외는 catch + 계속 진행")
    void run_runRuleException_continues() {
        DataQualityRule r1 = rule(1L, "users");
        DataQualityRule r2 = rule(2L, "orders");
        when(qualityMapper.findActiveRules()).thenReturn(List.of(r1, r2));
        when(statsMapper.countTable("users")).thenReturn(1);
        when(statsMapper.countTable("orders")).thenReturn(1);
        when(qualityService.runRule(r1)).thenThrow(new RuntimeException("boom"));

        int processed = job.run();

        // r1 실패 → processed 미증가, r2 정상 → processed=1
        assertThat(processed).isEqualTo(1);
        verify(qualityService).runRule(r2);
    }

    @Test
    @DisplayName("run — 활성 룰이 없으면 0 반환")
    void run_noActiveRules_returnsZero() {
        when(qualityMapper.findActiveRules()).thenReturn(List.of());

        int processed = job.run();

        assertThat(processed).isEqualTo(0);
        verify(qualityService, never()).runRule(any());
    }

    @Test
    @DisplayName("scheduled — GovernanceJobSupport.run 통한 batchLog start/success 호출")
    void scheduled_invokesBatchLogPipeline() {
        when(batchLog.start("DataQualityCheckJob", "QUALITY")).thenReturn(99L);
        when(qualityMapper.findActiveRules()).thenReturn(List.of());

        job.scheduled();

        verify(batchLog).start("DataQualityCheckJob", "QUALITY");
        verify(batchLog).success(eq(99L), eq(0));
        verify(batchLog, never()).failure(anyLong(), any());
    }

    @Test
    @DisplayName("scheduled — run 내부 예외는 GovernanceJobSupport에서 catch (failure 호출 안 됨, action은 catch)")
    void scheduled_runRuleException_isolatedFromBatchLog() {
        // 활성 룰 1건, 테이블 존재, runRule 예외 → run() 자체는 정상 (예외 catch)
        when(batchLog.start("DataQualityCheckJob", "QUALITY")).thenReturn(99L);
        when(qualityMapper.findActiveRules()).thenReturn(List.of(rule(1L, "users")));
        when(statsMapper.countTable("users")).thenReturn(1);
        when(qualityService.runRule(any())).thenThrow(new RuntimeException("inner"));

        job.scheduled();

        verify(batchLog).start("DataQualityCheckJob", "QUALITY");
        // run() 안에서 catch했으므로 success 경로
        verify(batchLog, times(1)).success(eq(99L), eq(0));
    }
}
