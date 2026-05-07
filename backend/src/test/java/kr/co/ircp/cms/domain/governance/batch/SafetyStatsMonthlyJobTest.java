package kr.co.ircp.cms.domain.governance.batch;

import kr.co.ircp.cms.domain.governance.repository.GovernanceStatsMapper;
import kr.co.ircp.cms.domain.governance.service.BatchExecutionLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SafetyStatsMonthlyJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-004 — 안전사고 월별 집계 (의존 SPEC 미존재 시 SKIP).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SafetyStatsMonthlyJob — 안전사고 통계 (REQ-DATA-004)")
class SafetyStatsMonthlyJobTest {

    @Mock private GovernanceStatsMapper statsMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private SafetyStatsMonthlyJob job;

    @Test
    @DisplayName("run(targetMonth) — UPSERT 호출")
    void run_targetMonth_invokesUpsert() {
        when(statsMapper.upsertSafetyStatsMonthly("2026-04")).thenReturn(2);

        int processed = job.run("2026-04");

        assertThat(processed).isEqualTo(2);
        verify(statsMapper).upsertSafetyStatsMonthly("2026-04");
    }

    @Test
    @DisplayName("run() — 전월 자동 계산 후 UPSERT")
    void run_default_invokesUpsert() {
        when(statsMapper.upsertSafetyStatsMonthly(anyString())).thenReturn(1);

        int processed = job.run();

        assertThat(processed).isEqualTo(1);
        verify(statsMapper).upsertSafetyStatsMonthly(anyString());
    }

    @Test
    @DisplayName("scheduled — safety_incidents 미존재 시 skip")
    void scheduled_tableMissing_skips() {
        when(statsMapper.countTable("safety_incidents")).thenReturn(0);
        when(batchLog.start("SafetyStatsMonthlyJob", "STATS")).thenReturn(3L);

        job.scheduled();

        verify(batchLog).skip(eq(3L), contains("safety_incidents"));
        verify(batchLog, never()).success(anyLong(), anyInt());
    }

    @Test
    @DisplayName("scheduled — 테이블 존재 시 정상 success")
    void scheduled_tableExists_runs() {
        when(statsMapper.countTable("safety_incidents")).thenReturn(1);
        when(batchLog.start("SafetyStatsMonthlyJob", "STATS")).thenReturn(4L);
        when(statsMapper.upsertSafetyStatsMonthly(anyString())).thenReturn(8);

        job.scheduled();

        verify(batchLog).success(eq(4L), eq(8));
    }
}
