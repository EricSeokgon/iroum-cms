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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BoardStatsMonthlyJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-001 — 게시판 월별 통계 집계.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BoardStatsMonthlyJob — 게시판 월별 통계 (REQ-DATA-001)")
class BoardStatsMonthlyJobTest {

    @Mock private GovernanceStatsMapper statsMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private BoardStatsMonthlyJob job;

    @Test
    @DisplayName("run(targetMonth) — upsertBoardStatsMonthly 호출")
    void run_targetMonth_invokesUpsert() {
        when(statsMapper.upsertBoardStatsMonthly("2026-04")).thenReturn(12);

        int processed = job.run("2026-04");

        assertThat(processed).isEqualTo(12);
        verify(statsMapper).upsertBoardStatsMonthly("2026-04");
    }

    @Test
    @DisplayName("run() — 전월 자동 계산 후 UPSERT")
    void run_default_invokesUpsert() {
        when(statsMapper.upsertBoardStatsMonthly(anyString())).thenReturn(5);

        int processed = job.run();

        assertThat(processed).isEqualTo(5);
        verify(statsMapper).upsertBoardStatsMonthly(anyString());
    }

    @Test
    @DisplayName("scheduled — 정상 실행 후 success 호출")
    void scheduled_invokesSuccess() {
        when(batchLog.start("BoardStatsMonthlyJob", "STATS")).thenReturn(6L);
        when(statsMapper.upsertBoardStatsMonthly(anyString())).thenReturn(3);

        job.scheduled();

        verify(batchLog).start("BoardStatsMonthlyJob", "STATS");
        verify(batchLog).success(eq(6L), eq(3));
    }
}
