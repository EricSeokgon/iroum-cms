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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PolicyMatchStatsJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-003 — 정책사업 매칭 월별 집계 (의존 SPEC 미존재 시 SKIP).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyMatchStatsJob — 정책 매칭 통계 (REQ-DATA-003)")
class PolicyMatchStatsJobTest {

    @Mock private GovernanceStatsMapper statsMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private PolicyMatchStatsJob job;

    @Test
    @DisplayName("run(targetMonth) — UPSERT 호출 + 행 수 반환")
    void run_targetMonth_invokesUpsert() {
        when(statsMapper.upsertPolicyMatchStatsMonthly("2026-04")).thenReturn(7);

        int processed = job.run("2026-04");

        assertThat(processed).isEqualTo(7);
        verify(statsMapper).upsertPolicyMatchStatsMonthly("2026-04");
    }

    @Test
    @DisplayName("run() — 전월 자동 계산 후 UPSERT 호출 (의존 검증은 SQL 결과)")
    void run_default_invokesUpsertForLastMonth() {
        when(statsMapper.upsertPolicyMatchStatsMonthly(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(3);

        int processed = job.run();

        assertThat(processed).isEqualTo(3);
        verify(statsMapper).upsertPolicyMatchStatsMonthly(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("scheduled — policy_matching 미존재 시 batchLog.skip 호출")
    void scheduled_tableMissing_skips() {
        when(statsMapper.countTable("policy_matching")).thenReturn(0);
        when(batchLog.start("PolicyMatchStatsJob", "STATS")).thenReturn(1L);

        job.scheduled();

        verify(batchLog).skip(eq(1L), contains("policy_matching"));
        verify(batchLog, never()).success(anyLong(), anyInt());
        verify(statsMapper, never()).upsertPolicyMatchStatsMonthly(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("scheduled — policy_matching 존재 시 정상 run 통한 success")
    void scheduled_tableExists_runs() {
        when(statsMapper.countTable("policy_matching")).thenReturn(1);
        when(batchLog.start("PolicyMatchStatsJob", "STATS")).thenReturn(2L);
        when(statsMapper.upsertPolicyMatchStatsMonthly(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(5);

        job.scheduled();

        verify(batchLog).success(eq(2L), eq(5));
    }
}
