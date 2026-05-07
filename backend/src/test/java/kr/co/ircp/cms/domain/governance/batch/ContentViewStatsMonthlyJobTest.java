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
 * ContentViewStatsMonthlyJob 단위 테스트.
 *
 * <p>SPEC-CMS-009 REQ-DATA-002 — 콘텐츠 월별 조회수 집계.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentViewStatsMonthlyJob — 콘텐츠 월별 통계 (REQ-DATA-002)")
class ContentViewStatsMonthlyJobTest {

    @Mock private GovernanceStatsMapper statsMapper;
    @Mock private BatchExecutionLogService batchLog;

    @InjectMocks private ContentViewStatsMonthlyJob job;

    @Test
    @DisplayName("run(targetMonth) — upsertContentViewStatsMonthly 호출")
    void run_targetMonth_invokesUpsert() {
        when(statsMapper.upsertContentViewStatsMonthly("2026-04")).thenReturn(15);

        int processed = job.run("2026-04");

        assertThat(processed).isEqualTo(15);
        verify(statsMapper).upsertContentViewStatsMonthly("2026-04");
    }

    @Test
    @DisplayName("run() — 전월 자동 계산 후 UPSERT")
    void run_default_invokesUpsert() {
        when(statsMapper.upsertContentViewStatsMonthly(anyString())).thenReturn(2);

        int processed = job.run();

        assertThat(processed).isEqualTo(2);
        verify(statsMapper).upsertContentViewStatsMonthly(anyString());
    }

    @Test
    @DisplayName("scheduled — 정상 실행 후 success 호출")
    void scheduled_invokesSuccess() {
        when(batchLog.start("ContentViewStatsMonthlyJob", "STATS")).thenReturn(8L);
        when(statsMapper.upsertContentViewStatsMonthly(anyString())).thenReturn(4);

        job.scheduled();

        verify(batchLog).success(eq(8L), eq(4));
    }
}
